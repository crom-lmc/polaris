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
   - **列表铺满 + 分页固定右下角（合同/财务所有模块统一，MUST）**：列表默认铺满页面剩余高度，分页条 MUST 用 `.ft-bottom` 的 sticky bottom:0 固定在右下角（z-index 5 高于操作列 3）、`margin:auto 0 0` 贴底，MUST NOT 让分页随内容流走或只固定在顶部；`pageSize` 默认 20、开 `showSizeChanger` 与 `showTotal`。树+表布局变体（如物料维护）用 `.panel-tree-main.ft-bottom` + `.tree-table-card` 让右侧表格撑满卡片、分页 sticky 贴底。列表骨架、空态铺满与树表变体细则见 `../../rules/frontend-01-L2-conventions.md`「表格与交互」。
   - **置灰操作按钮口径（合同/财务所有模块统一，MUST）**：禁用的链接式操作（删除/编辑等）只置灰、不改字号，MUST 用 `.link-disabled { color: var(--muted); cursor: not-allowed }`（继承 `<a>` 默认 14px，与相邻的「编辑/停用」同字号）；MUST NOT 用 `.muted`（带 `font-size:12px` 会让置灰按钮比其它操作小一号），也 MUST NOT 用 `a-button disabled` 的缩小样式。禁用原因用 `a-tooltip` hover 展示即可。
   - **常驻说明提示口径（合同/财务所有模块统一，MUST）**：列表页 MUST NOT 放常驻的说明性 `a-alert`（如「维护口径」）、顶部 `hint` 编码说明、左侧「共 N 个分类/条」计数等提示文字；页面只保留标题、搜索栏、操作按钮与表格。禁用原因、字段来源等解释只在 hover（`a-tooltip`/`title`）或弹窗里出现，不在页面常驻。
   - **表单标签左对齐口径（合同/财务所有模块统一，MUST）**：表单（含抽屉/弹窗内表单）字段标签文字 MUST 左对齐，必填星号 MUST NOT 挤占标签文字位置——必填项与非必填项的标签文字起点 MUST 在同一条竖线上。AntDV 4 的 `required` 靠 `label::before` 的 `inline-block + margin-inline-end` 占位，会推右必填项文字，因此两个应用的 `src/styles/global.css` MUST 统一覆盖（选择器 MUST 带 `.ant-form .ant-form-item` 前缀提高特异性，压过 AntDV CSS-in-JS 运行时的 `:not()` 规则）：`.ant-form .ant-form-item .ant-form-item-label > label { padding-left: 14px }` 预留星号位；`.ant-form .ant-form-item .ant-form-item-label > label.ant-form-item-required::before { position:absolute; left:0; top:50%; transform:translateY(-50%); margin-inline-end:0 }` 让星号落在预留区不占文字流。MUST NOT 让必填标签文字被星号推后、与非必填标签错位（2026-11-27 客户/供应商新增抽屉曾因此被要求整改）。
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
- 采购计划新建抽屉（2026-11-27 追加）：上传附件区 MUST 放在「计划明细（从物料库选料）」上方，明细行与计划金额在其后。
- `AttachmentUpload` 自带 `a-form-item`（含 `label` 与 `#extra` 提示），调用方 MUST NOT 再用 `<a-form-item label="...">` 包裹，否则出现重复标签（2026-11-27 新增凭证抽屉出现两个「凭证附件」被用户指出）；直接把组件放在表单内并用 `label` 属性命名即可。
- 合同侧三类附件走 `uploadContractAttachment(file, owner)`，owner 取 `purchase-plan`/`contract`/`acceptance`；上传按钮按独立权限码显示，列表加附件列时同步加宽 `scroll.x` 与 `--ft-min-width`。

## 预算科目业务归属口径（2026-11-27 落地）

- 预算科目是财务模块选择/维护的字段，**合同域（采购计划、合同台账）的表单、列表、详情 MUST NOT 出现预算科目**，前置业务员不掌握该概念；由财务域主数据页面与预算归集侧使用。
- 合同应用 MUST NOT 请求预算科目选项接口；design-web 设计稿中合同/计划的预算科目字段也须同步移除，避免「设计稿字段全补」时把错误归属字段补回。

## 合同/财务闭环字段完整性口径（2026-11-27 落地）

- 表单字段以设计稿为准，设计稿画出的字段 MUST 全部有录入位与列表/详情展示，不能只做流程；长文本列 `.cell-ellipsis` + `a-tooltip`，加列同步加宽 `scroll.x` 与 `--ft-min-width`。
- 合同付款计划：新增付款事由、关联凭证、附件列与录入（`uploadContractAttachment(file, 'payment-plan')`）；合同详情加执行步骤条，来源计划转入展示 `planNo`；变更所属项目时支持填变更原因（`updateContract(..., { remark })`）。
- 付款申请表单（2026-11-27 追加）：付款阶段 MUST 读 settings 字典 `contract.payment_stage` 下拉（列表/详情显示 label，不显示裸 value）；「应付金额」改名为「合同剩余金额」，选中合同后用合同 VO 的 `pendingAmount`（合同金额 - 已付 - 在途）只读自动带出；另加必填「本期付款金额」与必填「应付日期」，本期金额 MUST <= 合同剩余金额。合同详情内嵌编辑器通过父级传入 `remainingAmount`，不另造查询接口。
- 付款计划列表页按钮口径（2026-11-27 追加）：MUST NOT 同时放「新增计划」与「发起付款申请」两个都打开同一抽屉的按钮；列表页只保留「发起付款申请」，点击后保存计划并立即发起申请（保存到 WAITING 后调 apply），抽屉标题/按钮文案与行为一致。仅生成待申请计划（WAITING）的入口放合同详情的付款计划编辑器，再由列表行「发起申请」推进；发起失败（如验收未过账）时保留已存的 WAITING 计划供重试。
- 凭证：凭证类型读字典 `finance.voucher_type`，状态草稿/待审核/已入账（DRAFT/REVIEW/POSTED），列表展示审核人/过账人、附件列，新增/详情可上传凭证附件；操作列按状态给 提交审核/审核/过账。
- 报销（2026-11-27 追加）：报销类型读 `finance.expense_type` 并映射会计科目；「报销事由（字典）」`finance.expense_reason` 与类型重复，MUST NOT 出现在新建表单（历史列与字典保留兼容存量数据），自由文本「费用事由」保留用于凭证摘要/搜索。发票类型/税率读公共字典；**税额 = 金额 × 税率（字典值为百分数）自动计算且只读**，金额或税率变化即重算，MUST NOT 提供手输位。字典项走 `fetchPublicDict(typeCode)`，新增字典 MUST 已在 `PublicReferenceController.DICT_ALLOWED` 白名单，否则返回空数组。
- 应收应付（2026-11-27 追加）：新增单里的往来单位、关联合同号、会计科目、账期天数 MUST 用下拉，MUST NOT 手输——往来单位取 `/api/finance/partners/options?role=CUSTOMER|SUPPLIER`（合同域主数据经财务代理），合同取 `fetchContractOptions()`，会计科目取 `subjectOptions()`，账期取 settings 字典 `finance.credit_period`（30/60/90/180/365 天）。类型切换时清空并重拉往来单位；账期或发生日期变化即推算到期日（`date + creditDays`），日期仍可手工微调。

## 资源索引

- `references/frontend-guide.md`：前端完整规范与 11 应用公共契约。
- `references/common-ui-contracts.md`：从源码实证的公共模式与应用差异（含路径证据）。
- `references/new-feature-guide.md`：前端读取口径、UI 硬规范、交付自查。
- `references/platform-capabilities.md`：前端视角的横向能力复用清单。
