#!/usr/bin/env python3
"""根据 migrate_files.py 产出的 manifest 生成 file_id 回填 SQL（零第三方依赖）。

存量文件已上传到 file-service，但 cms_media / forum_attachment / ehr_attachment
三张业务表的 file_id 仍为空；本工具生成的 UPDATE 语句把它们回填，使业务读取链路
可以按 file_id 优先从统一文件服务取文件。

用法：
    python3 gen_file_id_backfill.py --manifest migrate_manifest.json > 07-file-id-backfill.sql
    mysql < 07-file-id-backfill.sql

说明：
- CMS 不依赖 manifest，直接用 file_alias.old_path = cms_media.url 跨库关联回填。
- forum 用 storage_path、EHR 用 file_key 与 manifest 的源文件相对路径匹配。
- 只更新 file_id IS NULL 的行，可重复执行；不会改动任何业务字段或文件。
"""

import argparse
import json
import sys
from pathlib import Path


def esc(value):
    return str(value).replace("\\", "\\\\").replace("'", "''")


def relative_of(src, root, fallback_parts):
    """优先按 --*-root 计算相对路径；没给 root 时取路径尾部固定段数。"""
    path = Path(src)
    if root:
        try:
            return path.relative_to(Path(root)).as_posix()
        except ValueError:
            pass
    return "/".join(path.parts[-fallback_parts:])


def main():
    ap = argparse.ArgumentParser(description="生成统一文件服务 file_id 回填 SQL")
    ap.add_argument("--manifest", default="migrate_manifest.json", help="migrate_files.py 产出的 JSON 清单")
    ap.add_argument("--cms-root", default="", help="CMS 旧上传根目录（可选，仅用于校验路径）")
    ap.add_argument("--forum-root", default="", help="论坛旧上传根目录（可选）")
    ap.add_argument("--ehr-root", default="", help="EHR 旧上传根目录（可选）")
    ap.add_argument("--cms-db", default="cms")
    ap.add_argument("--forum-db", default="forum")
    ap.add_argument("--ehr-db", default="ehr")
    ap.add_argument("--file-db", default="polaris_file")
    args = ap.parse_args()

    path = Path(args.manifest)
    if not path.exists():
        ap.error(f"清单不存在：{path}")
    data = json.loads(path.read_text(encoding="utf-8"))
    rows = data.get("manifest", data if isinstance(data, list) else [])
    rows = [r for r in rows if isinstance(r, dict) and r.get("file_id")]

    print("-- ============================================================================")
    print("-- 统一文件服务 · file_id 回填（由 gen_file_id_backfill.py 生成，幂等）")
    print("-- 只更新 file_id IS NULL 的业务行；执行前请确认 migrate_files.py 已 APPLY 完成")
    print("-- ============================================================================")
    print(f"USE `{args.file_db}`;")
    print()
    print("-- CMS：旧直链路径即 file_alias.old_path，跨库直接关联")
    print(f"UPDATE `{args.cms_db}`.`cms_media` m")
    print(f"  JOIN `{args.file_db}`.`file_alias` a ON a.old_path = m.url")
    print("   SET m.file_id = a.file_id")
    print(" WHERE m.file_id IS NULL AND a.file_id IS NOT NULL;")
    print()

    for app, db, table, column, root, parts in (
            ("forum", args.forum_db, "forum_attachment", "storage_path", args.forum_root, 4),
            ("ehr", args.ehr_db, "ehr_attachment", "file_key", args.ehr_root, 2)):
        selected = [r for r in rows if r.get("app") == app]
        print(f"-- {app}：按 manifest 的源文件相对路径匹配 {column}，共 {len(selected)} 条")
        for r in selected:
            rel = relative_of(r["src"], root, parts)
            print(f"UPDATE `{db}`.`{table}` SET file_id={int(r['file_id'])} "
                  f"WHERE {column}='{esc(rel)}' AND file_id IS NULL;")
        print()

    print("-- 校验：三张表的已回填数量（应接近 manifest 中的成功数）")
    print(f"SELECT 'cms' app, COUNT(*) n FROM `{args.cms_db}`.cms_media WHERE file_id IS NOT NULL")
    print("UNION ALL")
    print(f"SELECT 'forum', COUNT(*) FROM `{args.forum_db}`.forum_attachment WHERE file_id IS NOT NULL")
    print("UNION ALL")
    print(f"SELECT 'ehr', COUNT(*) FROM `{args.ehr_db}`.ehr_attachment WHERE file_id IS NOT NULL;")

    counts = {}
    for r in rows:
        counts[r.get("app")] = counts.get(r.get("app"), 0) + 1
    print(f"\n-- manifest 成功数：{counts}", file=sys.stderr)


if __name__ == "__main__":
    main()
