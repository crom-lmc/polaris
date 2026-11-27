---
description: Polaris 后端平台能力 L2 规范，新增业务功能或接入单号/审批/通知/埋点/字典/主数据/文件/审计时加载。
globs: ["polaris-server/**"]
alwaysApply: false
level: L2
---

# 后端平台能力接入规范

> 新增任何后端业务功能前逐项过一遍决策表；命中即接，禁止重复造轮子。L1 红线见 `00-L1-redlines.md`。
> 调用契约与示例见 `../skills/polaris-backend-development/references/new-feature-guide.md`。

## 决策表

| 需求特征 | MUST 接入 | MUST NOT |
|---|---|---|
| 业务编号 | settings `coderule` + `CodeRuleApi.next(tableName)` | 把空串直接写进业务字段（必须兜底） |
| 流程审批 | settings `approval` + `ApprovalApi`，业务侧只做单据与回调 | 自己实现流程引擎；回调不幂等 |
| 发消息/待办/邮件 | settings `notice` + `NoticeApi.send` | 用 `forum.notification` / `ehr.announcement` / `openapi.outbound` 冒充通知通道 |
| 可配参数/阈值/SLA | settings `embed` + `EmbedPointApi.get(code)` | 裸 JSON 配置、无引用点、无 null 兜底 |
| 选项型数据 | settings `dict`，类型按 `域.业务` 命名 | 每个应用各建一份本地表 |
| 关键实体 | settings `bizdata`，复用现有六类 | 在各服务写死枚举、另建本地表 |

## 接入契约

- MUST 判断顺序：先查可复用的字典/主数据，再决定是否新建；选项型无额外字段用字典，有独立字段需关联用主数据。
- MUST 每个埋点都有结构化维护表单：新增 key 时先在 settings `EmbedPointView.vue` 的 `EDITOR_BY_CODE` 登记 editor，且该 editor 的 JSON 字段名、默认值、取值范围必须按本 key 实现（MUST NOT 复用别的 key 的字段名）。未登记、只读预览或裸 JSON 编辑均视为缺陷，MUST NOT 交付。
- MUST 单号未配置返回空串时调用方兜底；埋点 `data` 可能为 null 时调用方兜底。
- MUST 消息参数使用 `ruleCode` / `accountIds` / `bizType` / `bizId` / `vars`，MUST NOT 自造字段名。
- MUST 审批回调用 `instanceId` + 状态校验幂等；重复投递返回 200，真异常返回 5xx 才触发 settings 重试。
- MUST 流程步骤的 `sla_hours` 在实例中用快照固化，改阈值不影响历史实例。

## 已落地能力复用

- MUST 文件与附件的**上传、存储、预览、下载全链路**走 `FileApi` + file-service：上传 `FileApi.upload`（`FileUploadRequest.content` 用 base64 内联是契约规定的传输方式，不是自建 base64 落库）、业务表只存 `file_id`、预览 `FileApi.sign` 出临时 URL（PRIVATE 文件）、下载带水印；MUST NOT 自建附件表、另存 URL、`MultipartFile` 自写本地落盘、本地静态资源映射，MUST NOT 以"统一服务失败"为由降级本地磁盘（CMS/forum/EHR 的本地回退仅用于读存量，新增上传 MUST NOT 照抄）。实证范式见 meeting `RoomFileController.upload` + `RoomService.decorate`。
- MUST 新 `bizType` 同步登记 `file_storage_policy`：`data.sql`（固定 ID 种子）+ `deploy/sql/NN-*.sql`（生产手工执行）各一条，`enabled=1`，`force_private`/`default_visibility`/`max_size_mb`/`allowed_ext` 按业务定；file-service 按 bizType 精确匹配、查不到抛 8102，无 default 兜底、不会自动建策略。缺策略的上传接口等于不可用。
- MUST PRIVATE 文件走 HMAC 签名 URL；下载附件 MUST 带水印，不能只在预览链路加水印。
- MUST 水印设置属于配置项且有真实引用点；上传前 MUST 校验配额。
- MUST 审计全局设置的每一项都有真实代码引用；没有实际功能的配置 MUST 去掉，不留空开关。
- MUST 导出行为强制留痕（手工记一条 SENSITIVE），MUST NOT 暴露关闭开关；导出权限码 `audit:events:export` 四联动。
- MUST 数据范围服务端注入；已知缺口（CMS fail-open、薪资未脱敏、forum/job/settings/portal/openapi 未接入）MUST NOT 照抄。
- MUST 新建业务表若数据天然归属组织（即使本期只按创建人过滤），预留的字段一律命名 **`org_code`**（存平台组织编码，如 `XC000100010001`），MUST NOT 用 `org_id` 或自造枚举代替；与 `DataScopeHelper.canAccessOrg(scopeCode, orgCode)` 的前缀匹配口径保持一致。
- MUST NOT 把 `scopeCode` 当成 `SELF / ORG / DEPT / ALL` 枚举：平台 `scopeCode` 是账号级数据权限根组织编码（如 `XC00010001`，支持逗号多值），空值一律 fail-closed 返回空集。详见 `../skills/polaris-backend-development/references/platform-capabilities.md` 第 4 节。
- MUST 新建单据落非空 `org_code`：无父表依赖的单据（采购计划等）服务端按 `AccountContext` 从 EHR 员工→组织树反查当前登录人部门编码，退回前端传值、再退回 `currentScopeCode()`；MUST NOT 只信前端传值落空串——`applyOrgScope` 的 `LIKE 'scopeCode%'` 必把空值过滤成空集，表现为「新建成功但列表查不到」。有父表/项目依赖的沿用父表带出，编辑时原 `org_code` 非空 MUST 保持不变。
- MUST 主子表一体的数据权限只在主表校验：明细/清单/付款计划子表查询 MUST NOT 再单独 `applyOrgScope`（主表已 `require(id)→assertCanAccessOrg` 或列表已过滤），否则历史空 `org_code` 子表被误过滤，表现为「详情明细空、明细数 0、金额 0」；子表落库 `org_code` 跟随主表，历史空值用幂等 SQL 按主表回填。
- MUST 采购计划转合同落完整主信息：`PlanConvertRequest` 接收 name/三个日期/note/fileIds，`createForPlan` 落库，请求缺省时回退计划名称/日期/附件；MUST NOT 只落 partnerCode/dept/owner 而让合同日期为空。

- MUST 全员应用（当前为 `portal` 北极星门、`forum` 北极星空、`meeting` 北极星会）同时落到 `ehr.account.default-apps` 埋点、EHR `AccountClient.FALLBACK_APPS` 兜底、前端 `AppCenterNav.PUBLIC_APP_CODES` 白名单与存量账号迁移脚本，且补 `forum` 应用授权时同步绑定 `FORUM_MEMBER` 角色；MUST NOT 只改新开账号默认值而遗漏存量账号。**授权一致性**：账号通过角色拥有某 `app_code` 的任意有效权限码时，Must 在 `sso_account_app` 补该应用授权（sso 权限码映射到 `sso-center`），否则应用中心隐藏入口且 OAuth 换票 403；非全员应用（如 `file` 北极星云）按显式授权维护，MUST NOT 因角色有权限码就自动全员开放。

## 不存在的后端能力（不要假设存在）

全局搜索、短信适配器、多租户、`@OperateLog` AOP、WebSocket/SSE 服务端推送、独立审批流程引擎。需要时用现有机制实现或先确认需求，MUST NOT 引入新依赖。
