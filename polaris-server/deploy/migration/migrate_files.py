#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
统一文件服务 · 存量存储迁移工具（Python 标准库实现，零第三方依赖）。

作用：
    扫描 CMS / forum / EHR 三套旧本地存储磁盘，逐个文件上传到 file-service，
    对 CMS 文件注册旧路径别名（file_alias），保证历史正文内嵌的旧图片链接不失效。
    forum / EHR 附件按数据库 ID 寻址、正文不暴露磁盘路径，旧文件保留原处即可兼容，
    本工具只上传副本 + 输出 file_id 清单，供后续按 file_id 切换读取。

    注：blog 服务已迁出本平台，其历史图片不属于本平台，不在迁移范围内。

安全设计：
    - 默认 dry-run：只扫描并打印将要处理的文件，不做任何写入。
    - 加 --apply 才真正上传；--apply 也只做「新增」，不删除任何旧文件（回滚安全）。
    - 单文件失败不影响其余；逐个打印进度与结果。
    - APPLY 模式下若 manifest 已存在，会自动跳过其中已成功（file_id 非空）的源文件，可安全重跑。

生产实际路径（2026-11-24 核实）：
    CMS   /opt/polaris/uploads/cms       （3 个 svg，多为 banner，无日期目录）
    forum /opt/polaris/uploads/forum     （16 个 png/svg/jpg）
    EHR   /opt/polaris/uploads/ehr       （220 个 txt/pdf）

用法：
    # 1) 先 dry-run 看清单（强烈建议）
    python3 migrate_files.py \
      --cms-root /opt/polaris/uploads/cms \
      --forum-root /opt/polaris/uploads/forum --ehr-root /opt/polaris/uploads/ehr

    # 2) 确认无误后真正执行
    SSO_INTERNAL_TOKEN='<...>' python3 migrate_files.py --apply \
      --cms-root /opt/polaris/uploads/cms \
      --forum-root /opt/polaris/uploads/forum --ehr-root /opt/polaris/uploads/ehr \
      --file-url http://127.0.0.1:9011 --manifest migrate_manifest.json

内部接口（均由 X-Internal-Token 准入）：
    POST /internal/files/upload         base64 上传，返回 file_object（含 id）
    POST /internal/files/alias/register 注册旧路径 → 文件ID 别名
"""

import argparse
import base64
import json
import mimetypes
import os
import sys
import urllib.request
import urllib.error
from pathlib import Path

# 业务类型 → file-service 存储策略（与 file_storage_policy 种子一致）
BIZ = {
    "cms": "cms_media",
    "forum": "forum_post",
    "ehr": "ehr_attachment",
}
# 可见性：CMS 媒体公开可直链；论坛附件公开；EHR 附件强制私有（策略也会覆盖）
VISIBILITY = {"cms": "PUBLIC", "forum": "PUBLIC", "ehr": "PRIVATE"}


def http_json(url, payload, token, timeout=120):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("X-Internal-Token", token)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def guess_mime(path):
    mime, _ = mimetypes.guess_type(str(path))
    return mime or "application/octet-stream"


def old_path_of(rel, app, cms_prefix):
    """按应用拼出历史正文里内嵌的旧链接路径（去掉 host，含前导 /）。"""
    if app == "cms":
        return f"{cms_prefix}/{rel}"
    # forum/ehr 正文按 ID 寻址，无内嵌路径
    return ""


def upload_one(file_path, app, root, file_url, token, cms_prefix, dry_run):
    """上传单个文件；返回 (new_file_id, old_path) 或 (None, old_path)。"""
    rel = file_path.relative_to(root).as_posix()
    old_path = old_path_of(rel, app, cms_prefix)

    if dry_run:
        print(f"  [dry-run] {app:5s} {rel}  -> old_path={old_path or '(按ID寻址)'}")
        return None, old_path

    raw = file_path.read_bytes()
    payload = {
        "bizType": BIZ[app],
        "visibility": VISIBILITY[app],
        "originalName": file_path.name,
        "contentType": guess_mime(file_path),
        "content": base64.b64encode(raw).decode("ascii"),
        "ownerType": "MIGRATION",
        "ownerId": app,
        "uploaderId": 0,
    }
    resp = http_json(f"{file_url}/internal/files/upload", payload, token)
    if resp.get("code") != 0 or not resp.get("data"):
        raise RuntimeError(f"上传失败: {resp}")
    new_id = resp["data"]["id"]

    if old_path:
        http_json(f"{file_url}/internal/files/alias/register", {
            "oldPath": old_path,
            "fileId": str(new_id),
            "sourceApp": app,
        }, token)
    print(f"  [ok] {app:5s} {rel} -> file_id={new_id} old_path={old_path or '(按ID寻址)'}")
    return new_id, old_path


def scan(root, app, cms_prefix, file_url, token, apply, done):
    if not root:
        return [], []
    base = Path(root)
    if not base.exists():
        print(f"!! 目录不存在，跳过: {base}", file=sys.stderr)
        return [], []
    print(f"\n=== 扫描 {app} : {base} ===")
    manifest, errors = [], []
    files = sorted(p for p in base.rglob("*") if p.is_file())
    print(f"共 {len(files)} 个文件")
    for i, f in enumerate(files, 1):
        if str(f) in done:
            print(f"  [skip] {f}（manifest 中已成功）")
            continue
        try:
            new_id, old_path = upload_one(f, app, base, file_url, token,
                                          cms_prefix, dry_run=not apply)
            manifest.append({"app": app, "src": str(f), "file_id": new_id, "old_path": old_path})
        except Exception as e:
            errors.append({"src": str(f), "error": str(e)})
            print(f"  [ERROR] {f}: {e}", file=sys.stderr)
        if i % 50 == 0:
            print(f"  ...进度 {i}/{len(files)}")
    return manifest, errors


def main():
    ap = argparse.ArgumentParser(description="统一文件服务存量迁移工具")
    ap.add_argument("--cms-root", default="", help="CMS 旧上传根目录")
    ap.add_argument("--forum-root", default="", help="forum 旧上传根目录")
    ap.add_argument("--ehr-root", default="", help="EHR 旧附件根目录")
    ap.add_argument("--cms-url-prefix", default="/api/cms/media/file", help="CMS 旧直链前缀")
    ap.add_argument("--file-url", default="http://127.0.0.1:9011", help="file-service 地址")
    ap.add_argument("--internal-token", default=os.environ.get("SSO_INTERNAL_TOKEN", ""),
                    help="X-Internal-Token（默认读环境变量 SSO_INTERNAL_TOKEN）")
    ap.add_argument("--apply", action="store_true", help="真正执行；不带则 dry-run 只打印清单")
    ap.add_argument("--manifest", default="migrate_manifest.json", help="迁移结果清单输出路径")
    args = ap.parse_args()

    roots = [(args.cms_root, "cms"), (args.forum_root, "forum"), (args.ehr_root, "ehr")]
    if not any(root for root, _ in roots):
        ap.error("至少指定一个 --*-root")

    # APPLY 模式下读取已有 manifest，跳过已成功的源文件（断点续传）
    done = set()
    if args.apply and Path(args.manifest).exists():
        try:
            old = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
            if old.get("mode") == "APPLY":
                done = {m["src"] for m in old.get("manifest", []) if m.get("file_id")}
                print(f"断点续传：manifest 中已有 {len(done)} 个成功文件，将跳过")
        except Exception as e:
            print(f"!! 读取旧 manifest 失败，将全量重跑: {e}", file=sys.stderr)

    all_manifest, all_errors = [], []
    for root, app in roots:
        m, e = scan(root, app, args.cms_url_prefix, args.file_url, args.internal_token, args.apply, done)
        all_manifest.extend(m)
        all_errors.extend(e)

    # 与断点续传的历史成功记录合并，保证 manifest 始终是全量清单
    if done:
        try:
            old = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
            all_manifest = [m for m in old.get("manifest", []) if m.get("file_id")] + all_manifest
        except Exception:
            pass

    mode = "APPLY" if args.apply else "DRY-RUN"
    by_app = {}
    for m in all_manifest:
        by_app[m["app"]] = by_app.get(m["app"], 0) + 1
    print(f"\n===== 完成（{mode}）：成功 {len(all_manifest)}（{by_app}），失败 {len(all_errors)} =====")
    with open(args.manifest, "w", encoding="utf-8") as fp:
        json.dump({"mode": mode, "manifest": all_manifest, "errors": all_errors},
                  fp, ensure_ascii=False, indent=2)
    print(f"清单已写入 {args.manifest}")
    if all_errors:
        sys.exit(2)


if __name__ == "__main__":
    main()
