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
   - **进度列百分比只显示一次（合同/财务所有模块统一，MUST）**：AntDV `a-progress` 默认 `showInfo=true` 自带百分比文字；若同时渲染 `<span class="rate-text">{{ rate }}%</span>` 会重复。进度条 + 外部百分比文字的形态 MUST 给 `a-progress` 加 `:show-info="false"`（合同台账执行进度、财务预算执行率、设计稿对应列）；只用进度条时不加外部文字。
   - **操作列不折叠 + 横向滚动条贴分页（所有后台应用统一，MUST）**：操作列最后一列的单元格/表头 MUST `white-space:nowrap`，`a-space` 与 `.action-links` MUST `flex-wrap:nowrap` 且 item `flex:0 0 auto`；按钮按状态最多可同时出现几个就给足列宽，并同步加宽 `scroll.x` 与 `--ft-min-width`（合同台账 170、采购计划 180、项目档案 200、凭证 220），MUST NOT 让「详情 / 清单」「删除」等断行。横向滚动条 MUST 落在分页条正上方而不是紧贴首行数据：`_shared/styles/responsive.css` 统一给 `.ft-scroll.ft-bottom .ant-table-container` 设 `flex:1 1 auto; min-height:0; display:flex; flex-direction:column`，`.ant-table-content` 设 `flex:1 1 auto; min-height:0`，让表格内容区撑满剩余高度；MUST NOT 让少量数据时滚动条停在数据行下方、与分页之间留大片空白。
   - **窄屏列不折叠（所有后台应用统一，MUST）**：列表列宽在任何屏宽下 MUST NOT 被压缩/折叠；`_shared/styles/responsive.css` MUST NOT 在 `@media (max-width:768px)` 把 `--ft-min-width` 覆写为 `0px`。≤768px 时各页 `--ft-min-width` 继续生效（选择器限定 `[style*='--ft-min-width']` 的后代表格，避免无声明表格被 0px `!important` 压扁），多出的宽度由 `.ft-scroll` 出横向滚动条，表头与操作列保持单行；MUST NOT 用隐藏列、换行或省略号代替横向滚动。
   - **置灰操作按钮口径（合同/财务所有模块统一，MUST）**：禁用的链接式操作（删除/编辑等）只置灰、不改字号，MUST 用 `.link-disabled { color: var(--muted); cursor: not-allowed }`（继承 `<a>` 默认 14px，与相邻的「编辑/停用」同字号）；MUST NOT 用 `.muted`（带 `font-size:12px` 会让置灰按钮比其它操作小一号），也 MUST NOT 用 `a-button disabled` 的缩小样式。禁用原因用 `a-tooltip` hover 展示即可。
   - **常驻说明提示口径（合同/财务所有模块统一，MUST）**：列表页 MUST NOT 放常驻的说明性 `a-alert`（如「维护口径」）、顶部 `hint` 编码说明、左侧「共 N 个分类/条」计数等提示文字；页面只保留标题、搜索栏、操作按钮与表格。禁用原因、字段来源等解释只在 hover（`a-tooltip`/`title`）或弹窗里出现，不在页面常驻。
   - **工具栏不重复放页面标题 / 右侧说明（合同/财务/采购计划所有模块统一，MUST）**：`ft-head.toolbar` MUST NOT 放 `<h2>` 独立页面标题及紧随的 `divider`，也 MUST NOT 在右侧放 `hint`/`panel-hint` 常驻说明（如「审批通过后转采购合同并带入清单」）；侧栏/页签已标明页面名，工具栏只留筛选控件、`grow` 与操作按钮。卡片/表单内的静态区块标题保留。`polaris-design-web` 设计稿对应页面同步适用。
   - **详情抽屉内嵌区块同样不放常驻说明（合同/财务，MUST）**：合同/财务详情抽屉里的内嵌区块（如合同清单、付款计划）工具栏 MUST NOT 放 `hint`（如「合同金额由清单合计派生」「付款计划从属于合同」）常驻说明；区块标题（`drawer-section-title`）只写短名（「合同清单」「付款计划」），MUST NOT 带括号说明（如「（在详情内维护，不做独立菜单）」）。需要留按钮时用 `<span class="grow" />` 占位保持靠右。字段来源、派生口径等解释只在 hover 或弹窗出现，不在页面常驻。
   - **主子表新建后引导到编辑页（合同，MUST）**：合同主信息与清单/付款计划是主子表关系，清单依赖合同号、付款计划依赖清单合计派生的金额，MUST NOT 在新建表单里内嵌清单/付款计划的子表编辑（避免先建号再批量落子的复杂事务与校验时序）。新建合同保存成功后 MUST 用返回的合同 VO 自动打开**编辑抽屉**（`openEdit(saved)`），在编辑页维护清单与付款计划，MUST NOT 提示「可在详情中维护」（详情已改为纯只读）。
   - **合同主信息不放「合同标的 / 付款条款」（合同/采购计划转合同，MUST）**：这两个字段的语义已由合同清单（标的具体化）和付款计划（付款条款执行明细）完整承载，主信息表单与详情描述列表 MUST NOT 再出现「合同标的」「付款条款 / 付款 / 验收条款」录入位或展示位（含采购计划转合同抽屉、设计稿对应页面）；仅保留后端字段兼容历史数据，不作为新增/编辑的录入项。用户 2026-11-27 明确取消。
   - **合同详情只读 / 编辑页承载子表（MUST，2026-11-27 用户定稿）**：合同详情抽屉 MUST 纯查看——状态 `a-tag` 只读、无按钮，所属项目/变更原因等写操作 MUST NOT 出现在详情；内嵌的清单与付款计划编辑器 MUST 传 `readonly`，隐藏工具栏按钮与操作列。`ContractItemsEditor`/`PaymentPlanEditor` 用 `readonly` prop 统一控制，readOnly 时工具栏与操作列不渲染、数据仍可读。列表操作列「详情 / 清单」文案统一为「查看」，状态流转按钮按状态收敛到操作列（草稿→提交审批；销售审批中→审批通过/退回草稿；已生效→开始执行/终止合同；执行中→完成合同/终止合同）。合同列表操作列 MUST 仅在 `status === 'DRAFT'` 且有编辑权限时显示「编辑」，点击复用新增合同抽屉并回填完整主信息（`openEdit(row)` + `editingId`），抽屉标题/保存按钮文案随编辑态变化，保存走 `updateContract(id, payload)`；合同类型在编辑态 MUST 禁用。编辑抽屉内 MUST 内嵌「合同清单」「付款计划」编辑器（可新增/编辑），footer「保存修改」旁 MUST 有「提交审批」按钮，点击先校验并保存主信息、再走状态机 `changeContractStatus(id, 'PENDING')`；新建态不显示该按钮。MUST NOT 在详情抽屉里再塞任何写操作。
   - **合同详情抽屉全站复用（MUST，2026-11-27 定稿）**：合同详情 MUST 抽成 `@/components/ContractDetailDrawer.vue` 公共组件（只读，含步骤条/合同信息/变更记录/只读清单与付款计划），合同台账「查看」与采购计划「关联合同号」/详情「关联采购合同」MUST 共用，MUST NOT 在各页面复制详情抽屉模板。组件支持 `contractId`（id）与 `contractNo`（合同编号）两种入参：按编号走 `getContractByNo`（`GET /api/contract/contracts/by-no/{no}`）。点合同编号 MUST 打开合同详情而不是采购计划详情。
   - **采购计划转入清单的预览与禁删（MUST，2026-11-27 定稿）**：转合同抽屉（`isConvert`，合同号未生成）MUST 只读预览即将带入的 `convertPlan.items`（行号/物料/规格/单位/数量/单价/金额 + 合计），MUST NOT 渲染依赖合同号的 `ContractItemsEditor`（会按空 contractNo 查不到数据而显示空）；保存成功后父页面切回编辑态才由编辑器加载真实清单。采购计划转入的清单行以备注前缀「由采购计划 」标记为来源行：`ContractItemsEditor` MUST 对来源行隐藏「删除」、用 `.link-disabled` + `a-tooltip` 说明（编辑/验收仍保留），编辑弹窗里来源行备注框只读；后端 `ContractItemServiceImpl.delete()` MUST 对来源前缀行拒绝删除、编辑时强制保留该前缀，防止抹掉备注绕过禁删。二者双防线，MUST NOT 只做前端隐藏。
   - **合同表单抽屉跨页面复用（MUST，2026-11-27 定稿）**：合同台账的新增/编辑与采购计划「转合同」MUST 共用 `@/components/ContractFormDrawer.vue`，MUST NOT 在采购计划页另写简化版转合同抽屉（四字段版已废弃）。组件通过 `contract` / `convertPlan` 两个 props 区分编辑与计划转入：计划转入时预填合同名称=计划名称、归属部门=计划需求部门、负责人=计划申请人、签订/生效日期=计划日期、到期日期=计划需求日期、附件与 orgCode 沿用计划；往来单位/所属项目仍由主数据弹窗选择（计划无这两个字段）。保存时组件按模式调 `saveContract` / `updateContract` / `convertPurchasePlan`，`saved` 事件回传合同 VO；父页面在新建/转合同成功后把该 VO 作为 `contract` 回传，抽屉切换为编辑态展示清单与付款计划（后端转合同时已复制计划明细），MUST NOT 让用户关掉抽屉再手工找合同。
   - **表单标签左对齐口径（合同/财务所有模块统一，MUST）**：表单（含抽屉/弹窗内表单）字段标签文字 MUST 左对齐，必填星号 MUST NOT 挤占标签文字位置——必填项与非必填项的标签文字起点 MUST 在同一条竖线上。AntDV 4 的 `required` 靠 `label::before` 的 `inline-block + margin-inline-end` 占位，会推右必填项文字，因此两个应用的 `src/styles/global.css` MUST 统一覆盖（选择器 MUST 带 `.ant-form .ant-form-item` 前缀提高特异性，压过 AntDV CSS-in-JS 运行时的 `:not()` 规则）：`.ant-form .ant-form-item .ant-form-item-label > label { padding-left: 14px }` 预留星号位；`.ant-form .ant-form-item .ant-form-item-label > label.ant-form-item-required::before { position:absolute; left:0; top:50%; transform:translateY(-50%); margin-inline-end:0 }` 让星号落在预留区不占文字流。MUST NOT 让必填标签文字被星号推后、与非必填标签错位（2026-11-27 客户/供应商新增抽屉曾因此被要求整改）。
5. **自检**：按 L1 + L2 检查权限、表格、长文本、响应式、主题、回调、监听器清理、换行符与 BOM。
6. **验证**：`vue-tsc --noEmit` + `vite build` 通过；改 `_shared/` 时 11 个应用全量验证；本地清理守卫拦截时用 `--outDir /tmp/<唯一目录> --emptyOutDir false`。
7. **收尾**：在仓库外 `F:\code\迭代文档\polaris-server\update.md` 顶部追加记录；最终回复列明需发布的应用、无需重启后端、未提交/未部署状态；未经明确指令不提交不部署。提交时备注用中文（格式 `类型(范围): 中文概述`），前后端改动必须分开提交——前端代码一个 commit，后端代码及平台侧配套（SQL/Nacos/网关/部署）另一个 commit，不混提。

## 主数据弹窗选择口径（合同/财务所有模块统一，MUST）

- 往来单位、所属项目、负责人、归属部门等主数据字段 MUST 用弹窗选择（表格 + 搜索 + 分页 + 单选），MUST NOT 用 `a-select` 下拉或 `a-input` 手输。统一用 `_shared/components/EntityPickerModal.vue`：组件 API 无关，调用方注入 `load(params) => {records,total}` 与动态列 `columns`，`rowKey` 指定唯一字段，`confirm` 回传完整行对象（便于联动带出其它字段）。
- 表单里用 `a-input readonly`（`cursor:pointer`）点击打开弹窗，不再渲染下拉箭头；选中后把所需字段写入表单（value 存名称/编码与库中一致）。
- **弹窗保持紧凑（2026-11-27 截图反馈，MUST）**：`EntityPickerModal` 搜索栏右侧 MUST NOT 放「单击行选中，确定后回填」等常驻提示（`ep-tip`），操作说明只经按钮 `title` 或 hover 呈现。
- **详情空值不写解释性提示（2026-11-28 截图反馈，MUST）**：`a-descriptions-item` 等只读详情字段为空统一显示 `-`，MUST NOT 把「尚未生成（审批通过后由财务域建单回写）」这类流程说明塞进字段值里；需要解释时只放 tooltip 或帮助入口。
- **弹窗宽度三档（2026-11-27 定稿，MUST）**：**大弹窗 1200px** = 物料树+表格双栏的 `MaterialPickerModal`；**中弹窗 800px** = 供应商/项目/人员/部门等主数据单选的 `EntityPickerModal`（搜索框最大宽 260、`a-table` 滚动高 300）；**小弹窗 480px** = 仅需少量字段的简单确认/选择弹窗。MUST NOT 自行引入其它宽度档。
- **弹窗宽度 MUST 用数字绑定 `:width="数字"`（2026-11-27 bug，MUST）**：AntDV `a-modal` 的 `width` 写成字符串 `width="700"` 会渲染成无效 CSS（`width:700` 无单位被浏览器忽略），弹窗被表格内容撑到 `max-width:94vw`（约 1000px），视觉上远大于设定值。所有 `a-modal` 宽度 MUST 用 `:width="数字"` 绑定，`_shared` 的 `EntityPickerModal`/`MaterialPickerModal` 尤其注意，MUST NOT 写回裸字符串。
- **弹窗内分页固定右下角（2026-11-27 定稿，MUST）**：带表格分页的弹窗（`EntityPickerModal`/`MaterialPickerModal`）MUST 遵守与列表页 `.ft-bottom` 相同的规则——表格体高度恒定（`scroll.y` 对应加 `:deep(.ant-table-body) { min-height: <y>px !important }`），分页 `:deep(.ant-table-pagination)` MUST `position:sticky; bottom:0; z-index:5; margin:auto 0 0` 贴底，数据少时也不得上移；MUST NOT 让分页随内容流走。
- **Teleport 弹窗的 footer/内容样式 MUST 用 `:global()`（2026-11-28 bug，MUST）**：`a-modal` 通过 Teleport 渲染到 body，组件根节点没有 `data-v-xxx`，`<style scoped>` 里的 `.batch-modal :deep(.ant-modal-footer)` 编译成 `.batch-modal[data-v-xxx] .ant-modal-footer` 永远匹配不上，样式静默失效（按钮紧贴 textarea 的 `0/200` 计数、反复调 padding 无效）。正确写法：`:global(.batch-modal .ant-modal-footer) { padding: 30px 16px 18px !important }` + `:global(.batch-modal .batch-footer) { display:flex; width:100%; justify-content:flex-end }`。自定义 footer 容器 MUST 加 `width:100%` 才能真右对齐。仅 `a-modal`/`a-drawer` 被 teleport 的根节点需要 `:global`，弹窗内部普通子元素仍可 scoped。`vite build` 后检查产物 CSS 是否带 `[data-v-` 即可验证。
- 合同类型联动：采购合同的往来单位只能选 SUPPLIER/BOTH（label 显示「供应商」），销售合同只能选 CUSTOMER/BOTH（label 显示「客户」）；切换类型时清空已选避免角色错配。服务端也做同样校验，前端过滤不是唯一防线。
- 负责人选中后 MUST 自动带出其所属部门写入归属部门（只读、不可手改）；部门为空时给出提示不允许保存。
- 财务侧选项多为一次性加载的候选，用 `useEntityPickers.ts` 的 `paginatePicker`/`orgRows` 做本地过滤分页；合同侧可直连后端分页接口。
- 字典枚举字段（企业类型、结算账期、账期天数、付款阶段、评级等）不属于主数据实体，MUST 用 settings 字典下拉，MUST NOT 手输；与主数据弹窗口径不冲突。往来单位档案「结算账期」复用 `finance.credit_period`（30/60/90/180/365），存量「30 天账期」文本在展示/编辑时归一化为档位值（提取数字匹配字典 value）后再保存；提交时若字典已加载 MUST 校验选中值为字典档位，字典加载失败可降级放行。

## 合同台账采购/销售菜单拆分口径（2026-11-27 落地，MUST）

- 合同台账在 SSO 权限树拆为父菜单 `contract:ledger:list` + 两个二级菜单 `contract:ledger:purchase:list` / `contract:ledger:sales:list`；侧栏 MUST 按权限树 `parentCode` 渲染 `a-sub-menu`，前端只写权限码→路由/图标映射（父码映射 `/contract` 仅作目录，子码映射各自路由）。业务接口 `@Permit` 仍用父码，角色授权 MUST 同时含父子码，否则子菜单可见但接口 403。
- 两个二级菜单 MUST 复用同一个 `ContractLedgerView`（路由 `meta.contractType` 固定 `PURCHASE`/`SALES`），组件监听 `route.path` 变化按新类型重查（Vue Router 复用同一组件实例，`onMounted` 只触发一次）。列表 MUST 隐藏类型筛选（菜单已定类型），列名、搜索占位、表单与详情标签按类型显示「供应商」/「客户」，新增表单锁定当前菜单类型并清空往来单位避免角色错配。

## 合同状态流转口径（2026-11-27 落地，MUST）

- 合同详情里的「状态」MUST 只读展示（`a-tag`），MUST NOT 用 `a-select` 让用户直接选 DRAFT/PENDING/EFFECTIVE/EXECUTING/COMPLETED/TERMINATED；状态是流程结果，不是可编辑业务字段。
- 状态流转 MUST 由明确按钮或服务端条件推进，**按钮统一放在合同列表操作列**（详情纯只读不放任何状态按钮；草稿编辑页 footer 另有「提交审批」入口）。按当前状态收敛：草稿→「提交审批 / 编辑 / 删除」；采购/销售合同审批中均不提供手工审批按钮（接入审批中心后等回调，只保留「审批信息」）；已生效 / 执行中→「验收 / 终止合同」；已完成/已终止无按钮。**「编辑 / 提交审批 / 删除」MUST 仅在 `status === 'DRAFT'` 显示**（2026-11-27 用户定稿），非草稿合同是业务凭证不可编辑/删除；服务端 `update()`/`delete()` 同步硬校验。危险流转（终止）先 `Modal.confirm`，确认后再调状态接口。
- **执行中 / 完成不提供手工按钮（2026-11-27 定稿）**：首次验收由服务端自动推进「已生效→执行中」，全部付款结清自动推进「执行中→完成」，前端 MUST NOT 再显示「开始执行 / 完成合同」按钮。已生效/执行中的合同在列表操作点「验收」打开独立抽屉，复用 `ContractItemsEditor` 并传 `contract-status`，此时只开放验收与验收记录、隐藏清单增删改。
- **清单/付款按钮按状态显隐（2026-11-27 定稿）**：`ContractItemsEditor` 收 `contract-status`，新增/编辑/删除仅 DRAFT（`canEditItems`），验收仅 EFFECTIVE/EXECUTING（`canAccept`）；`PaymentPlanEditor` 收 `contract-status`，「发起申请」仅 EFFECTIVE/EXECUTING 显示，且服务端会再校验验收完成度。前端显隐只是可见入口，服务端校验才是防线。
- 前端按钮 MUST NOT 自行拼状态值绕过状态机，统一调 `changeContractStatus(id, target)`；服务端按状态机白名单校验，前端按钮集合只是可见入口，不是唯一防线。
- 提交审批（`target=PENDING`）前端校验：列表操作列用 `row.itemCount` / `row.paymentTermList` 静态判断；编辑抽屉清单用 `listItems(contract.no)` 实时查，为空 `message.warning('请先维护合同清单，再提交审批')` 并中止，不弹确认框。**付款节点 MUST NOT 用 `listPaymentTerms` 静态查库判断（2026-11-28 bug）**：用户在 `PaymentTermsEditor` 填了节点看到「合计 100%」却没点「保存节点」，库中仍为空，静态查库会误拦「请先配置付款节点」。正确做法：`PaymentTermsEditor` 通过 `defineExpose({ save })` 暴露保存能力（`save(silent)` 校验比例=100%/含验收款/比例>0，返回 boolean），`ContractFormDrawer` 用 `termsRef` 在提交审批前 `await termsRef.value.save()` 自动保存编辑态节点，失败才中止；确认弹窗 `onOk` 内再保存一次防期间修改。付款计划可后建（在付款申请页发起时创建），MUST NOT 作为提交审批的前置校验。
- **所有合同提交审批一律进审批中心（2026-11-28 用户定稿）**：确认文案 MUST NOT 再出现「未达审批线直接生效」等绕过审批的措辞，统一为「提交后将进入审批流程，由审批中心审批通过后合同才生效，确认吗？」。提交后状态停在 PENDING 等回调，前端不得假设低金额合同会直接生效。
- 验收弹窗的「验收日期」MUST 必填：`a-form-item` 带 `required` 星号，`submitAccept` 未选日期时 `message.warning('请选择验收日期')` 并中止；后端 `accept()` 同步硬校验 `ACCEPTANCE_DATE_REQUIRED`，前端校验只是可见入口（2026-11-27 用户定稿）。验收结果值 MUST 用中文：采购「合格/不合格」、销售「通过/不通过」，与后端入库、付款埋点及推应收口径一致。
- **已发起审批的单据 MUST 在操作列提供「审批信息」入口（2026-11-27 用户定稿，全站通用）**：任何接入审批中心的业务列表（付款计划、采购计划、采购/销售合同、报销等），凡是进入过审批流程（`status !== 'WAITING'/'DRAFT'` 等初始态）的行，操作列 MUST 有只读「审批信息」按钮。**详情页面 MUST 与审批中心「我发起的」点开的详情是同一张页面（2026-11-27 用户定稿）**：展示表单快照（版式配置 sections / 未配置时铺开 formData）、流程进度步骤条（发起申请 + 各审批节点）、每个步骤的审批人/状态/意见；MUST NOT 另造「标题+状态+发起人+tasks timeline」的简化抽屉。取数口径：业务域后端按 `bizType+bizId` 反查拿 instanceId（`approvalApi.latest`，多模板取雪花 ID 最大，String 下发防精度丢失），前端用该 ID 调账号中心 `GET /api/accounts/approvals/{id}` + `/context` 渲染同款详情——与审批中心共用同一套接口/版式，不做第二套展示。合同域范式：后端 `ContractApprovalInfoSupport.latest/toVO` 收口 + `GET /api/contract/{resource}/{id}/approval` 反查 ID；前端共用 `components/ApprovalDetailDrawer.vue`（props `open/instanceId/width`，`v-model:open`，只读不渲染通过/驳回/撤回），三个列表（付款计划/采购计划/合同台账）都复用它，MUST NOT 复制详情模板；已删除的旧 `ApprovalInfoDrawer.vue` 不得再引入。注意：详情接口有「参与者」校验（发起人/审批人/委托人/抄送人），非参与者打开显示空态，与审批中心口径一致。**详情抽屉右下角同样 MUST 有「查看审批」按钮**（与操作列同口径 `status !== 初始态`，复用同一详情抽屉），用户从详情即可查看审批过程，无需退回列表（2026-11-27 补充）。
- **接入审批中心后 MUST 撤掉手工审批按钮（2026-11-27 定稿）**：单据提交后审批/驳回由审批中心回调推进，列表操作列与详情 footer MUST NOT 再显示「审批 / 驳回 / 审批通过」手工入口（否则绕过审批中心、无审批记录）。服务端同步拒绝在途实例的 `approve/reject/changeStatus`。采购计划 PENDING 只保留「审批信息 + 转合同（APPROVED 时）」，销售合同 PENDING 无手工动作按钮。
- **撤手工审批按钮 MUST 同时保留「提交审批」入口（2026-11-28 踩坑）**：撤掉的是「审批/驳回/审批通过」等中途手工决策按钮，NOT 初始态的「提交审批」。每个接入审批中心的单据（DRAFT/REJECTED 等初始态）MUST 在操作列与详情 footer 保留「提交审批」按钮，点击调后端 `POST /{id}/submit` 走审批中心发起。采购计划曾因只撤按钮未补提交入口，导致草稿单据只有「详情」、流程卡死无法往下流转。判定口径：初始态（DRAFT/WAITING/REJECTED）=「提交审批」；在途/终态 =「审批信息」，APPROVED 才出下游动作（转合同/付款等）。

## 付款节点与销售客户验收交互口径（2026-11-27 落地，MUST）

- **合同编辑抽屉内付款节点配置**：`PaymentTermsEditor` 嵌在合同清单与付款计划之间，仅 DRAFT 可编辑；保存前 MUST 校验比例合计 100%、包含验收款、每项比例 > 0，比例合计非法时红色提示。金额由服务端按清单合计锁定；**编辑态「锁定金额」列 MUST 实时预览**（`listItems` 求 amount 合计 × 比例，四舍五入两位，带「预览」标记，hover 说明"保存后锁定"），不能显示后端旧快照的 ¥0 让用户以为算错（2026-11-28 截图反馈）。清单增删后须刷新预览合计：`ContractItemsEditor` 在 add/edit/delete 后 emit `changed`，表单层维护 `itemsVersion` 传给节点组件 watch 重算合计（只重算合计，不覆盖用户正在编辑的比例/阶段）。只读态直接显示后端 `amount`。`contract_header.warranty_due_date` 用独立日期字段（质保金节点需填，到期后才可申请），详情抽屉也要展示。合同详情只读复用同一组件传 `readonly`，MUST NOT 复制第二张节点表。
- **提交审批前置校验（2026-11-28 用户定稿）**：DRAFT 合同点「提交审批」前 MUST 校验已有清单 AND 付款节点（`ContractVO.paymentTermList` 非空），缺项分别提示「请先维护合同清单」「请先配置付款节点，再提交审批（比例合计 100%，须含验收款）」，不弹确认框；服务端 `changeStatus(PENDING)` 同步硬校验防绕过。已生效存量合同不受影响。
- **付款申请按节点选择**：`PaymentPlanEditor` / `PaymentPlanView` 申请时 MUST 先 `listPaymentTerms(contractNo)`，选择 `termId` 后阶段与可申请金额由节点决定；阶段展示用 `paymentStageLabel`，MUST NOT 再让用户手输阶段。`WARRANTY` 未到期节点置灰不可选；`GOODS` 阶段 MUST 出现已交付清单行多选（`itemIds`），否则后端 8434。金额上限 = 节点「可申请金额」而非合同剩余金额，超限后端 8432。列表/详情展示节点名称与所选清单行。存量无节点合同沿用原「全验收」逻辑，不必强制补节点。
- **合同编辑抽屉不显示付款计划区（2026-11-28 用户定稿）**：付款节点取代手工付款计划后，`ContractFormDrawer` 编辑态 MUST NOT 渲染 `PaymentPlanEditor`（原空区块无意义，用户截图要求去掉）；付款计划只在「付款申请」页发起时创建，`ContractDetailDrawer` 仍传 `readonly` 只读展示。删区块时同步移除未用的 import。
- **到期提醒独立列（2026-11-28 截图反馈）**：付款计划列表的 `overdue` / `dueSoon`（「已逾期」红色 / 「临近到期」橙色）MUST 单独成列展示，MUST NOT 混在「应付日期」列里；新增列后必须同步加宽 `scroll.x`（1410→1530）与列宽合计，避免 sticky 操作列被挤压。
- **销售客户验收**：`ContractItemsEditor` MUST 收 `contractType`，`SALES` 时弹窗标题「客户验收」、结果「通过/不通过」、显示「客户验收单号」选填；提交 `customerNo`。验收记录弹窗在销售下列出客户验收单号、应收单号、推应收失败原因（hover tooltip）与「重试推应收」按钮（`pushAcceptanceAr`），只读态隐藏重试。验收记录按钮只读态也要可见，便于合同详情查看应收状态；财务核销回调尚未接通，MUST NOT 前端自行推断核销状态。
- **权限与显隐**：验收按钮统一 `EFFECTIVE/EXECUTING` 且 `deliveredQty < qty` 且 `!acceptanceDone`（列表由 `acceptanceDone` 派生字段隐藏），销售验收通过后同样隐藏，不再显示「验收完」的中间状态或手工完成按钮。所有调用 `ContractItemsEditor` 的地方（合同编辑抽屉、合同台账验收抽屉、合同详情抽屉）MUST 传 `contractType`，否则销售会误显示采购的「合格/不合格」。

## 当前登录人/部门默认带出口径（合同/财务所有模块统一，MUST）

- 「申请人/需求部门/归属部门」默认当前登录人及其部门时，MUST 先调服务端 `my-profile` 端点（finance `/api/finance/expenses/my-profile`、contract `/api/contract/purchase-plans/my-profile`）取服务端按 `AccountContext` 从 EHR 反查的值；本地用 `store.me.accountId` 匹配 `employees` 推导仅作 EHR 不可用时的兜底。
- **MUST NOT 只用 `accountId` 本地匹配带出部门**：19 位雪花 ID 在浏览器以 number 解析会丢精度，匹配失败时部门静默为空（2026-11-27 采购计划「需求部门」带不出来即此根因）；申请人有 `store.displayName` 兜底才显示正常，容易掩盖部门缺失。
- `openCreate` 类方法改为 async 先等 profile 返回再打开抽屉，避免 readonly 输入框在 profile 到达前先渲染空值。

## 财务报销特有口径（2026-11-27 落地）

- 发起报销先调 `/api/finance/expenses/my-profile` 取服务端反查的申请人/归属部门，禁止再用 `store.me.accountId` 本地匹配 EHR 员工（19 位雪花 ID 会丢精度导致部门为空）。
- 报销类型 MUST 读 settings 字典 `finance.expense_type`；会计科目是**只读展示**，由报销类型 `remark` 映射经后端带出，不给非财务人员暴露科目下拉。
- 报销凭证附件 MUST 调 `/api/finance/expense/files/upload`（FileReader→base64），表单只暂存 `fileId` 与签名预览 URL，提交 `fileIds` 数组；不得直连文件服务或另存 URL。

## 统一附件上传口径（2026-11-27 落地）

- 上传组件统一用 `_shared/components/AttachmentUpload.vue`（API 无关，调用方注入 `upload` 函数与 `fileIds` v-model；错误通过 `error` 事件交给应用提示，共享组件不 import 应用 UI 库）。
- 业务表单只存 `fileId` 数组，FileReader→base64 调业务服务代理接口；预览走后端签发的 `attachmentUrls`，MUST NOT 直连文件服务或把签名 URL 当长期地址存库。
- 采购计划新建抽屉（2026-11-27 追加）：上传附件区 MUST 放在「计划明细（从物料库选料）」上方，明细行与计划金额在其后。
- `AttachmentUpload` 自带 `a-form-item`（含 `label`），调用方 MUST NOT 再用 `<a-form-item label="...">` 包裹，否则出现重复标签（2026-11-27 新增凭证抽屉出现两个「凭证附件」被用户指出）；直接把组件放在表单内并用 `label` 属性命名即可。
- **附件上传说明不常驻（2026-11-27 截图反馈，MUST）**：`AttachmentUpload` MUST NOT 用 `#extra` 把「支持 jpg/png/...（最多 N 个，单文件不超过 M MB）」常驻在表单下方；格式/数量/大小说明只放上传按钮的 `title`（hover 呈现），组件内不再渲染 `#extra`。与弹窗 `ep-tip` 常驻提示口径一致。
- 合同侧三类附件走 `uploadContractAttachment(file, owner)`，owner 取 `purchase-plan`/`contract`/`acceptance`；上传按钮按独立权限码显示，列表加附件列时同步加宽 `scroll.x` 与 `--ft-min-width`。

## 预算科目业务归属口径（2026-11-27 落地）

- 预算科目是财务模块选择/维护的字段，**合同域（采购计划、合同台账）的表单、列表、详情 MUST NOT 出现预算科目**，前置业务员不掌握该概念；由财务域主数据页面与预算归集侧使用。
- 合同应用 MUST NOT 请求预算科目选项接口；design-web 设计稿中合同/计划的预算科目字段也须同步移除，避免「设计稿字段全补」时把错误归属字段补回。

## 合同/财务闭环字段完整性口径（2026-11-27 落地）

- 表单字段以设计稿为准，设计稿画出的字段 MUST 全部有录入位与列表/详情展示，不能只做流程；长文本列 `.cell-ellipsis` + `a-tooltip`，加列同步加宽 `scroll.x` 与 `--ft-min-width`。
- 合同付款计划：新增付款事由、关联凭证、附件列与录入（`uploadContractAttachment(file, 'payment-plan')`）；合同详情加执行步骤条，来源计划转入展示 `planNo`；变更所属项目时支持填变更原因（`updateContract(..., { remark })`）。
- 付款申请表单（2026-11-27 追加）：付款阶段 MUST 读 settings 字典 `contract.payment_stage` 下拉（列表/详情显示 label，不显示裸 value）；「应付金额」改名为「合同剩余金额」，选中合同后用合同 VO 的 `pendingAmount`（合同金额 - 已付 - 在途）只读自动带出；另加必填「本期付款金额」与必填「应付日期」，本期金额 MUST <= 合同剩余金额。合同详情内嵌编辑器通过父级传入 `remainingAmount`，不另造查询接口。
- 付款申请派生金额口径（2026-11-27 追加）：付款申请/付款计划表单 MUST 有只读「剩余未付金额」= 合同剩余金额 - 本期付款金额，随两者变化实时重算（金额按 2 位小数四舍五入，避免浮点误差）；该字段为纯展示派生值，MUST NOT 落库或作为提交参数，服务端仍以本期付款金额 <= 合同剩余金额为准校验。
- 付款计划列表页按钮口径（2026-11-27 追加）：MUST NOT 同时放「新增计划」与「发起付款申请」两个都打开同一抽屉的按钮；列表页只保留「发起付款申请」，点击后保存计划并立即发起申请（保存到 WAITING 后调 apply），抽屉标题/按钮文案与行为一致。仅生成待申请计划（WAITING）的入口放合同详情的付款计划编辑器，再由列表行「发起申请」推进；发起失败（如验收未完成/合同状态不符）时保留已存的 WAITING 计划供重试。付款申请不以验收单过账（POSTED）为前提——验收登记完成即可发起，过账在应付单之后发生。
- 凭证：凭证类型读字典 `finance.voucher_type`，状态草稿/待审核/已入账（DRAFT/REVIEW/POSTED），列表展示审核人/过账人、附件列，新增/详情可上传凭证附件；操作列按状态给 提交审核/审核/过账。
- 报销（2026-11-27 追加）：报销类型读 `finance.expense_type` 并映射会计科目；「报销事由（字典）」`finance.expense_reason` 与类型重复，MUST NOT 出现在新建表单（历史列与字典保留兼容存量数据），自由文本「费用事由」保留用于凭证摘要/搜索。发票类型/税率读公共字典；**税额 = 金额 × 税率（字典值为百分数）自动计算且只读**，金额或税率变化即重算，MUST NOT 提供手输位。字典项走 `fetchPublicDict(typeCode)`，新增字典 MUST 已在 `PublicReferenceController.DICT_ALLOWED` 白名单，否则返回空数组。
- 应收应付（2026-11-27 追加）：新增单里的往来单位、关联合同号、会计科目、账期天数 MUST 用下拉，MUST NOT 手输——往来单位取 `/api/finance/partners/options?role=CUSTOMER|SUPPLIER`（合同域主数据经财务代理），合同取 `fetchContractOptions()`，会计科目取 `subjectOptions()`，账期取 settings 字典 `finance.credit_period`（30/60/90/180/365 天）。类型切换时清空并重拉往来单位；账期或发生日期变化即推算到期日（`date + creditDays`），日期仍可手工微调。

## 资源索引

- `references/frontend-guide.md`：前端完整规范与 11 应用公共契约。
- `references/common-ui-contracts.md`：从源码实证的公共模式与应用差异（含路径证据）。
- `references/new-feature-guide.md`：前端读取口径、UI 硬规范、交付自查。
- `references/platform-capabilities.md`：前端视角的横向能力复用清单。
