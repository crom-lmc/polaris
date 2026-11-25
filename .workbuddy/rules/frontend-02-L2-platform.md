---
description: Polaris 前端平台能力 L2 规范，新增业务页面或接入字典/应用中心/水印/审计导出/SLA 展示时加载。
globs: ["polaris-web/**"]
alwaysApply: false
level: L2
---

# 前端平台能力接入规范（polaris-web/**）

> 新增任何前端业务页面前逐项过一遍决策表；命中即接，禁止重复造轮子。L1 红线见 `00-L1-redlines.md`。
> 读取口径与示例见 `../skills/polaris-frontend-development/references/new-feature-guide.md`。

## 决策表

| 需求特征 | MUST 接入 | MUST NOT |
|---|---|---|
| 选项型数据（下拉/单选） | settings `dict`，`GET /api/settings/public/dicts/<typeCode>` | 写死在前端常量、每个应用各建一份本地列表 |
| 可配参数/阈值/SLA 展示 | 由后端接口随业务数据返回；**每个**阈值/开关埋点都在 settings 有结构化编辑表单并登记 `EDITOR_BY_CODE` | 前端直读 settings 内部接口、裸 JSON 编辑入口、未登记埋点只读兜底 |
| 业务编号/审批/消息 | 只展示结果与发起动作，配置与调用全在后端 | 前端自行生成编号、拼接审批流、自建通知 |
| 文件附件 | `file_id` + `/api/file/{public,secure,admin,legacy}/**` | 另存 URL、自建附件上传页 |
| 水印展示/下载 | 复用已有水印链路 | 只在预览加水印而下载不带 |

## 接入契约

- MUST 先确认本应用解包口径（A / B / C）再写 API；字典读取的返回类型按口径声明。
- MUST 单号、审批、通知的配置页面只在 settings，前端不重复实现配置 UI。
- MUST 审计导出按钮挂 `audit:events:export`，导出行为由后端留痕，前端不提供关闭开关。
- MUST 应用中心正式名称从 `sso_application.name` 动态取，不写死中文名。

## 已落地能力复用

- MUST 应用中心用 `_shared/AppCenterNav.vue`，签码先同步开窗再请求，callback 用完整 `redirect_uri`。
- MUST 水印覆盖图片/PDF/SVG/DOCX/XLSX/PPTX，下载链路同样带水印。
- MUST 审计设置页每一项都有真实代码引用；无实际功能的配置不展示。
- MUST SLA 阈值在列表/统计页可见可改；所有埋点 MUST 在 `EmbedPointView.vue` 登记结构化表单，字段名/默认值/范围与本 key 对齐，禁止"未登记只读"作为交付状态。

## 不存在的前端能力（不要假设存在）

全局搜索、前端共享 npm 包（只有 `_shared/` 源码目录）、WebSocket/SSE 客户端封装、短信适配器、多租户、`v-perm` 指令。需要时用现有机制实现或先确认需求，MUST NOT 引入新依赖。
