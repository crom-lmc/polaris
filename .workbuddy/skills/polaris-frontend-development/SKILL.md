---
name: polaris-frontend-development
description: Polaris 一体化企业平台前端开发流程（polaris-web/，11 个独立 Vue3 + Vite5 + TS 应用 + _shared 共享目录）。当需要在该仓库新增或修改页面、组件、路由、API 封装、权限、主题、响应式、SSO 回调，或做前端类型检查与构建前应加载本 Skill；本 Skill 应在任何涉及 Polaris 前端代码的任务开始时优先加载。
agent_created: true
---

# Polaris 前端开发 Skill

## 角色

### 你是一名资深的前端开发工程师

先读代码再写代码：11 个应用的响应解包口径、token 键名、401 行为并不一致，动手前先确认本应用写法，不跨应用照抄。

## 规则层级（工作区根 `.workbuddy/rules/`，红线不在此重复）

- **L1 红线**：`../../rules/frontend-00-L1-redlines.md`（alwaysApply，MUST/MUST NOT）— 违反即拒绝交付。
- **L2 规范**：`../../rules/frontend-01-L2-conventions.md`（API/权限/表格/主题/响应式/回调）、`../../rules/frontend-02-L2-platform.md`（平台能力接入）。
- **L3 准则**：`../../rules/frontend-03-L3-guidelines.md`（SHOULD，不阻断）。

优先级：L1 > L2 > 本 Skill 流程 > L3 > 模型默认。规范与代码冲突时以代码为准，并提示同步修正文档。

## 按需加载参考

1. **结构与工程写法**：`references/frontend-guide.md`（11 应用清单、API/路由/组件/样式、响应式、权限、构建）。
2. **代码实证的公共契约**：`references/common-ui-contracts.md`（`_shared` 三件套 API、三套解包口径、四套 401 行为、store/token 键名差异、`ft-*` 类定义位置、CRLF/BOM/监听器清理）。
3. **新业务功能**：`references/new-feature-guide.md`（前端能力读取口径 + UI 硬规范 + 自查）。
4. **已落地能力**：`references/platform-capabilities.md`（应用中心与 SSO、文件与水印、审计设置与导出、SLA 可视化、不存在的能力）。

## 标准流程

1. **定范围**：确认改动落在哪个应用；若改 `_shared/`，列出全部 11 个应用为影响面。
2. **查口径**：读本应用 `src/api/request.ts`、`stores/user.ts`、`stores/theme.ts`、路由守卫，确认解包口径/401 行为/token 键名/base 三件套。
3. **做决策**：新页面按 `../../rules/frontend-02-L2-platform.md` 判断是否接字典/应用中心/水印等能力，配置类数据不写死前端。涉及侧栏菜单时，先对照 SSO 权限树的 `name/sort/parentCode`（`/api/auth/me?app=<code>` 的 `menus`），前端只写路由/图标映射，不写菜单名称与顺序。
4. **实现**：`<script setup lang="ts">`；选物料统一接 `_shared/components/MaterialPickerModal.vue`（懒加载分类树 + 分页搜索），禁止再写全量下拉；列表页用 `ft-scroll ft-bottom` + `.ft-table` CSS sticky 固定操作列 + 表头 `align` 一致（表头 `white-space: nowrap`，列宽合计超过 `scroll.x` 时同步加宽）；`ft-head` 加 `.toolbar`（gap/margin-bottom 12px）让标题、搜索栏、卡片之间留白；长文本用 `.cell-ellipsis` 省略 + `a-tooltip` 悬浮展示完整值；**合同/财务所有模块 MUST NOT 在表格上方加由选中节点或筛选值拼出的额外动态标题**（如「子分类：X」「物料清单：X」），静态区块标题保留；颜色走 CSS 变量。
5. **自检**：按 L1 + L2 检查权限、表格、长文本、响应式、主题、回调、监听器清理、换行符与 BOM。
6. **验证**：`vue-tsc --noEmit` + `vite build` 通过；改 `_shared/` 时 11 个应用全量验证；本地清理守卫拦截时用 `--outDir /tmp/<唯一目录> --emptyOutDir false`。
7. **收尾**：在仓库外 `F:\code\迭代文档\polaris-server\update.md` 顶部追加记录；最终回复列明需发布的应用、无需重启后端、未提交/未部署状态；未经明确指令不提交不部署。提交时备注用中文（格式 `类型(范围): 中文概述`），前后端改动必须分开提交——前端代码一个 commit，后端代码及平台侧配套（SQL/Nacos/网关/部署）另一个 commit，不混提。

## 财务报销特有口径（2026-11-27 落地）

- 发起报销先调 `/api/finance/expenses/my-profile` 取服务端反查的申请人/归属部门，禁止再用 `store.me.accountId` 本地匹配 EHR 员工（19 位雪花 ID 会丢精度导致部门为空）。
- 报销类型 MUST 读 settings 字典 `finance.expense_type`；会计科目是**只读展示**，由报销类型 `remark` 映射经后端带出，不给非财务人员暴露科目下拉。
- 报销凭证附件 MUST 调 `/api/finance/expense/files/upload`（FileReader→base64），表单只暂存 `fileId` 与签名预览 URL，提交 `fileIds` 数组；不得直连文件服务或另存 URL。

## 统一附件上传口径（2026-11-27 落地）

- 上传组件统一用 `_shared/components/AttachmentUpload.vue`（API 无关，调用方注入 `upload` 函数与 `fileIds` v-model；错误通过 `error` 事件交给应用提示，共享组件不 import 应用 UI 库）。
- 业务表单只存 `fileId` 数组，FileReader→base64 调业务服务代理接口；预览走后端签发的 `attachmentUrls`，MUST NOT 直连文件服务或把签名 URL 当长期地址存库。
- 合同侧三类附件走 `uploadContractAttachment(file, owner)`，owner 取 `purchase-plan`/`contract`/`acceptance`；上传按钮按独立权限码显示，列表加附件列时同步加宽 `scroll.x` 与 `--ft-min-width`。

## 合同/财务闭环字段完整性口径（2026-11-27 落地）

- 表单字段以设计稿为准，设计稿画出的字段 MUST 全部有录入位与列表/详情展示，不能只做流程；长文本列 `.cell-ellipsis` + `a-tooltip`，加列同步加宽 `scroll.x` 与 `--ft-min-width`。
- 合同付款计划：新增付款事由、关联凭证、附件列与录入（`uploadContractAttachment(file, 'payment-plan')`）；合同详情加执行步骤条，来源计划转入展示 `planNo`；变更所属项目时支持填变更原因（`updateContract(..., { remark })`）。
- 凭证：凭证类型读字典 `finance.voucher_type`，状态草稿/待审核/已入账（DRAFT/REVIEW/POSTED），列表展示审核人/过账人、附件列，新增/详情可上传凭证附件；操作列按状态给 提交审核/审核/过账。
- 报销：报销事由/发票类型/税率读公共字典，税额手输；字典项走 `fetchPublicDict(typeCode)`，新增字典 MUST 已在 `PublicReferenceController.DICT_ALLOWED` 白名单，否则返回空数组。
- 应收应付：新增发票号、发票类型、账期天数、到期日字段与列表列；到期日可由 `date + creditDays` 派生，前端可覆盖。

## 资源索引

- `references/frontend-guide.md`：前端完整规范与 11 应用公共契约。
- `references/common-ui-contracts.md`：从源码实证的公共模式与应用差异（含路径证据）。
- `references/new-feature-guide.md`：前端读取口径、UI 硬规范、交付自查。
- `references/platform-capabilities.md`：前端视角的横向能力复用清单。
