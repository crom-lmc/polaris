# 平台横向能力实证（后端视角）

> 本文件列出历次迭代中落地并线上验证过的平台能力，后端接入时先复用、不要重写。
> 所有条目均为事实描述；实现细节以仓库代码为准，发现冲突时以代码为准并修正文档。

## 1. 统一文件服务（file-service :9011）

- 调用链：业务服务 → `file-client` 的 `FileApi`（`/internal/files/**`）→ `file-service`（库 `polaris_file`）。
- 数据模型：`file_object`（对象元数据）、`file_storage_policy`（存储策略）、`file_alias`（业务别名）。**业务表只存 `file_id`，不自建附件表、不另存 URL**。
- **上传+预览全链路 MUST 走文件服务**（红线）：上传 `FileApi.upload`（`FileUploadRequest.content` 用 base64 内联是契约传输方式，不是自建 base64 落库）；预览 `FileApi.sign` 出临时 URL；PRIVATE 下载带水印。MUST NOT 本地 `MultipartFile` 落盘、`@Value` 自定义上传根目录、静态资源映射、base64 直存业务表、或以"统一服务失败"为由降级本地磁盘。
- **新 bizType 必须同步登记 `file_storage_policy`**（红线）：`FileService.policy(bizType)` 按 `biz_type` 精确匹配 + `enabled=1`，查不到抛 `POLICY_NOT_FOUND(8102)`/HTTP 404，**无 default 兜底、不会自动建策略**。新增上传接口写了 bizType 却漏登记策略，上传必然失败（meeting `meeting_room_image` 曾踩此坑）。登记方式：`file-service/src/main/resources/data.sql` 加固定 ID 种子（FILE_SQL_INIT_MODE=always 时重启自动写入）+ `deploy/sql/NN-*.sql` 加同 id 的 `INSERT IGNORE`（never 模式手工执行）。策略只管 `maxSizeMb`/`allowedExt`/`defaultVisibility`/`forcePrivate`/`quotaGb`/`ttlDays`，不管 bucket/path/签名。
- 新增上传接口范式（可直接抄）：meeting `RoomFileController.upload` 收 `{originalName, contentType, content(base64)}` → `fileApi.upload(bizType/visibility/owner/uploaderId)` → 返回 `fileId + sign 后的 url`；`RoomService.decorate` 按 `file_ids` 逗号串逐个 `fileApi.sign` 回填 `roomImages` 给前端预览。业务表 `meeting_room.file_ids` 只存 id。
- **存量本地回退只用于读历史**：CMS `LocalStorageService` + `WebMvcConfig` 静态映射、forum `ForumStorageService`、EHR `ehr.file-root` 是历史遗留，仅读取存量文件；新增上传 MUST NOT 复制这些降级分支。
- 访问控制：PUBLIC 直链；PRIVATE 走 HMAC 签名 URL，不绕开签名接口直出。
- 已实现能力（先复用）：附件（`file_id` 关联 + CMS 存量回填）、分享（提取码随机、有效期/次数限制）、回收站（标记删除与恢复）、配额（上传前校验，超限给业务错误码）、预览（图片/PDF）、水印（图片/PDF/SVG/DOCX/XLSX/PPTX；**下载链路也要加水印，不能只加在预览**）。
- 水印设置是配置项且必须有真实引用点；新增菜单/按钮仍需权限码四联动。

## 2. 统一审计（audit-server :9012）

- 链路：网关 `JwtAuthFilter` → `AuditReporter`（WebClient 异步）全量兜底，含 GET（只采 query 串，脱敏后写 `requestParams`，**不采 JSON body**）；body 详情由业务 AOP 写。
- `audit_event`（业务审计）+ `system_log`（由 common Logback Appender 采集 WARN/ERROR）；audit-server 自身关闭采集（`polaris.system-log.enabled=false`）防递归。
- 全局设置由 `AuditSettingService` 提供：采集开关、截断阈值、`queryParams` 等，**每项必须有真实代码引用，没有实际功能的配置项要去掉**。
- 合规下限：`LOGIN` / `SENSITIVE` 必留；登录事件记**尝试登录的用户名**（失败也记）；有主体记 USER，无主体内部调用记 SYSTEM。
- 支持 CSV 导出，导出行为强制留痕（手工记一条 SENSITIVE），不提供关闭开关；导出权限码 `audit:events:export` 四联动。
- 审计范围：登录才能做的动作全审计；匿名 `/public/**` 不在范围内。审计上报失败只 warn，绝不回滚业务。
- 脱敏规则在 common `AuditDataMasker` 与 audit-client 两份，改一处必须同步另一处。

## 3. SLA 阈值

- 审批 SLA 不写死在代码常量或裸 JSON：阈值放 settings 埋点配置，由 `EmbedPointView.vue` 提供结构化编辑器并登记 code。
- 流程步骤的 `sla_hours` 在实例中用快照固化，改阈值不影响历史实例。
- **每个埋点都必须有结构化维护表单**：新增 key 同步在 `EmbedPointView.vue` 的 `EDITOR_BY_CODE` 登记 editor，并按本 key 的 JSON 字段/默认值/范围实现；未登记只读或裸 JSON 编辑视为缺陷，不能绕开页面直接改库。

## 4. 数据权限现状（不要照抄缺口）

- 已接入：common 的 `DataScope` / `DataScopeHelper` / `ScopeCodeCache`，EHR / CMS 的 `applyOrgScope`。
- 未接入：forum / job / settings / portal / openapi。
- 已知缺口：CMS 的 applyOrgScope 存在 fail-open、薪资未脱敏；新功能不要复用这些缺口，**空范围一律返回空集**。

### 4.1 组织字段口径（MUST，新建表必看）

- 平台数据权限判断的是**组织编码前缀**：`DataScopeHelper.canAccessOrg(scopeCode, orgCode)` 内部为 `orgCode.startsWith(scopeCode)`（`polaris-common/.../security/DataScopeHelper.java:24-33`）。
- `scopeCode` 是账号级数据权限根组织编码（如 `XC00010001`），**支持逗号分隔多值**；它不是 `SELF / ORG / DEPT / ALL` 这类范围枚举（仓库里的 `SELF` 只是 `ResignApply.SOURCE_SELF` 单据来源常量）。禁止在方案或代码里假设这类枚举存在。
- **业务表凡涉及组织归属，预留字段一律命名 `org_code`，存 orgCode（如 `XC000100010001`），MUST NOT 用 `org_id`、MUST NOT 存组织名称或自造字典 code。**
- 即使本期只按 `creator_id` 查本人数据，也要预留 `org_code`：将来接组织数据权限时只加过滤逻辑、不改表结构；本期不因此改动 common / account 的数据权限代码。
- 空 scopeCode 或空 orgCode 一律 fail-closed（返回空集 / 403），不得放行全量。
- 实证位置：`common/DataScope.java:15-27`、`common/DataScopeHelper.java:24-33`、EHR `security/DataScopeService.applyOrgScope`、account `AccountContext.getDataScopeOrgCode()` 与 `data_scope_org_code` 表字段。

## 5. 应用登记与正式名称

- 应用中心签码走 `POST /api/auth/oauth/authorize`，`redirectUri` 必须是 account 中注册的完整地址，不能改成首页。
- 正式名称取自 `sso_application.name`：sso 北极星钥、audit 北极星眼、file 北极星云、openapi 北极星桥、cms 北极星源、ehr 北极星宿、settings 北极星典、job 北极星轨、forum 北极星空、forum-admin 北极星核、portal 北极星门。
- 端口：网关 9080、auth 9001、account 9002、cms 9004、portal 9005、ehr 9006、settings 9007、job 9008、forum 9009、openapi 9010、file 9011、audit 9012；`blog`（9003）已下线迁出，不要恢复。

## 6. 不存在的后端能力（不要假设存在）

全局搜索、短信适配器、多租户、`@OperateLog` AOP、WebSocket/SSE 服务端推送、独立审批流程引擎（业务侧统一走 settings.approval）。需要时用现有机制实现或先确认需求，不要引入新依赖。
