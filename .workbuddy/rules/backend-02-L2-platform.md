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
- MUST 埋点 key 在 `EmbedPointView.vue` 登记结构化编辑器；未登记的埋点页面只读，MUST NOT 绕开页面改库。
- MUST 单号未配置返回空串时调用方兜底；埋点 `data` 可能为 null 时调用方兜底。
- MUST 消息参数使用 `ruleCode` / `accountIds` / `bizType` / `bizId` / `vars`，MUST NOT 自造字段名。
- MUST 审批回调用 `instanceId` + 状态校验幂等；重复投递返回 200，真异常返回 5xx 才触发 settings 重试。
- MUST 流程步骤的 `sla_hours` 在实例中用快照固化，改阈值不影响历史实例。

## 已落地能力复用

- MUST 文件与附件走 `FileApi` + file-service，业务表只存 `file_id`；MUST NOT 自建附件表或另存 URL。
- MUST PRIVATE 文件走 HMAC 签名 URL；下载附件 MUST 带水印，不能只在预览链路加水印。
- MUST 水印设置属于配置项且有真实引用点；上传前 MUST 校验配额。
- MUST 审计全局设置的每一项都有真实代码引用；没有实际功能的配置 MUST 去掉，不留空开关。
- MUST 导出行为强制留痕（手工记一条 SENSITIVE），MUST NOT 暴露关闭开关；导出权限码 `audit:events:export` 四联动。
- MUST 数据范围服务端注入；已知缺口（CMS fail-open、薪资未脱敏、forum/job/settings/portal/openapi 未接入）MUST NOT 照抄。

## 不存在的后端能力（不要假设存在）

全局搜索、短信适配器、多租户、`@OperateLog` AOP、WebSocket/SSE 服务端推送、独立审批流程引擎。需要时用现有机制实现或先确认需求，MUST NOT 引入新依赖。
