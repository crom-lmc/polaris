# 存量迁移说明

- [统一文件服务 · 存量存储迁移](#统一文件服务--存量存储迁移说明)
- [统一审计中心 · 存量日志迁移](#统一审计中心--存量日志迁移)

---

# 统一文件服务 · 存量存储迁移说明

## 背景

此前三套存储各自落盘（**生产实际路径 2026-11-24 核实**）：
CMS（`/opt/polaris/uploads/cms`）、forum（`/opt/polaris/uploads/forum`）、
EHR（`/opt/polaris/uploads/ehr`）。
统一文件服务上线后，需要把存量文件纳入管理，同时**保证历史正文里的旧图片链接不失效**。

> blog 服务已迁出本平台（独立运行），其历史图片不属于本平台，不在迁移范围内。

## 兼容策略（完整兼容，零回归风险）

三类附件的寻址方式不同，采取差异化策略：

| 应用 | 旧链接形态 | 正文是否内嵌路径 | 迁移策略 |
|---|---|---|---|
| CMS | `/api/cms/media/file/<相对路径>` 静态直链 | 是（文章正文） | 上传新文件 + 注册 `file_alias` 旧路径映射，旧链接继续有效 |
| forum | `/api/forum/attachments/{id}` 按 ID | 否（按附件 ID） | 上传副本留档；旧文件保留原处，读取链路不变 |
| EHR | `/api/ehr/attachment/{id}/download` 按 ID | 否（按附件 ID） | 上传副本留档；旧文件保留原处，读取链路不变 |

**关键点**：CMS 正文里内嵌的是静态路径，搬迁后路径会变，靠 `file_alias(old_path → file_id)` 映射 +
file-service 的 `/api/file/legacy/**` 等兼容端点，把旧路径指回新文件。forum/EHR 正文只存附件 ID，
不暴露磁盘路径，因此保留旧文件即可 100% 兼容——这也是为什么新上传切换可以分阶段、不急于一口气做完。

**策略前置**：EHR 历史附件大量为 `.txt`（合同存档）、forum 历史封面含 `.svg`，默认上传策略不允许；
迁移前需放宽 `ehr_attachment` 允许 `txt`、`forum_post` 允许 `svg`（见 `data.sql` 种子），否则这两类文件会被策略拒绝。

## 迁移步骤

### 0. 前置

1. 部署 file-service（`:9011`），确认 `polaris_file` 库已建 `file_object` / `file_storage_policy` / `file_alias` 三表。
2. 确认 `file_storage_policy` 已放宽：EHR 允许 `txt`、forum 允许 `svg`；种子见 `data.sql`。
3. 执行 `deploy/sql/05-file-storage-migration.sql`，给三张存量表加 `file_id` 列（幂等，MySQL 5.7 兼容）。
4. 拿到内部令牌 `SSO_INTERNAL_TOKEN`（生产在 `/opt/polaris/.env`）。

### 1. Dry-run（只扫描，不写入）

```bash
python3 deploy/migration/migrate_files.py \
  --cms-root /opt/polaris/uploads/cms \
  --forum-root /opt/polaris/uploads/forum \
  --ehr-root /opt/polaris/uploads/ehr
```

检查输出清单：应有 cms 3 + forum 16 + ehr 220 = 239 个文件；
CMS 的 `old_path` 形如 `/api/cms/media/file/banner-arch.svg`，与实际正文一致。

### 2. 正式执行

```bash
export SSO_INTERNAL_TOKEN='<与 file-service 一致>'
python3 deploy/migration/migrate_files.py --apply \
  --cms-root /opt/polaris/uploads/cms \
  --forum-root /opt/polaris/uploads/forum \
  --ehr-root /opt/polaris/uploads/ehr \
  --file-url http://127.0.0.1:9011 \
  --manifest migrate_manifest.json
```

工具会：
- 逐个上传文件到 file-service（base64 走 `/internal/files/upload`）；
- CMS 文件额外注册旧路径别名（`/internal/files/alias/register`）；
- 输出 `migrate_manifest.json`，含每个文件的 `file_id` 与 `old_path`；
- 单文件失败不中断，结束时汇总成功/失败；重跑会跳过 manifest 中已成功的文件（断点续传）。

**只新增、不删除任何旧文件**，可随时重跑（alias 注册幂等覆盖）。

### 3. 验证

- 打开一篇含历史图片的 CMS 文章，确认旧直链能正常显示（此时旧文件还在，走的是 CMS 原路径；
  若要验证 alias 生效，可临时把某个旧文件改名，再访问该链接，应能从 file-service 读回）。
- 抽查 `migrate_manifest.json`，确认无失败项。

### 4. 回填业务表 file_id（切换前必须执行）

迁移脚本只把文件放进 file-service 并注册别名，**没有回写三张业务表的 file_id**。
读取链路切换后 forum/EHR 靠该列寻址，因此必须先回填：

```bash
python3 deploy/migration/gen_file_id_backfill.py \
  --manifest migrate_manifest.json \
  --forum-root /opt/polaris/uploads/forum \
  --ehr-root /opt/polaris/uploads/ehr > /tmp/07-file-id-backfill.sql
mysql < /tmp/07-file-id-backfill.sql
```

脚本只更新 `file_id IS NULL` 的行，可重复执行；CMS 部分直接用 `file_alias.old_path = cms_media.url`
跨库关联，不依赖 manifest。

### 5. 读取链路与新上传切换（已实施）

- 网关新增 `cms-file-legacy` 精确路由：`/api/cms/media/file/**` → file-service 的 legacy 端点，
  排在 `/api/cms/**` 之前。正文里的旧直链由 `file_alias` 映射到迁移后的文件。
- forum/EHR 的 `AttachmentService` 按 `file_id` 优先从 file-service 读字节，读不到自动回退旧本地文件；
  权限校验仍在业务侧完成，私有文件字节不对外暴露。
- 新上传统一走 `FileApi.upload`（CMS `cms_media`/PUBLIC、forum `forum_post`/PUBLIC、
  EHR `ehr_attachment`/PRIVATE 强制），fileId 写入业务表；file-service 不可用时逐条回退旧本地落盘，
  并在日志里告警。CMS 新上传同时注册稳定别名，保证写进正文的相对路径长期有效（签名 URL 会过期）。
- 删除时同步对 file-service 做软删；旧本地文件一律保留不删，便于回滚。

## 回滚

旧文件原地保留，回滚分两步：①把网关 `cms-file-legacy` 路由删除（或下调优先级），CMS 恢复本地 ResourceHandler；
②清空三张业务表的 `file_id`（`UPDATE ... SET file_id=NULL`），forum/EHR 即回退本地读取。
若需撤销别名，删除 `polaris_file.file_alias` 中对应记录即可。

---

# 统一审计中心 · 存量日志迁移

## 迁移范围

| 来源 | 目标类别 | app | 说明 |
|---|---|---|---|
| `sso_ms.sso_audit_event` | 原样保留 | 原值 | 网关 ACCESS / 登录 LOGIN / 业务 BUSINESS |
| `sso_ms.sso_audit_log` | `SENSITIVE` | `sso` | 改密、角色/权限变更等敏感操作 |
| `sso_ms.sso_login_log` | `LOGIN` | `sso` | 登录成功/失败，含登录方式与失败原因 |
| `ehr.ehr_audit_log` | `SENSITIVE` | `ehr` | 明文查看 / 导出 / 导入 |

四类记录统一进 `polaris_audit.audit_event` 单表。用来源 ID + 固定偏移生成目标 ID，配合 `NOT EXISTS`
保证脚本可重复执行；旧表**不自动删除**，核对无误后再按脚本末尾的注释手工处理。

## 步骤

1. 启动 audit-server（`:9012`），确认 `polaris_audit.audit_event` 已建好。
2. 部署新版本网关、sso-account、ehr-service，确认审计上报链路通了（新事件写入 audit-server）。
3. 低峰期执行：`mysql -h <host> -uroot -p < polaris-server/deploy/sql/06-audit-migration.sql`。
4. 按脚本第 6 节的校验 SQL 核对各类数量与分布；确认无误后再考虑删旧表。
5. 前端 audit-web 部署到 `polaris-audit.laimengchao.site`，事件页可按「敏感操作」类别查看原 EHR 审计记录。

## 回滚

迁移只做 INSERT 与一条权限 `UPDATE`（把 `log` / `account:log:view` / ehr `audit*` 标记为 deleted）。
如需回滚，删除 `polaris_audit.audit_event` 中对应来源 ID 的记录，并把 `sso_ms.sso_permission` 的
`deleted` 改回 0 即可；旧表数据始终保留。
