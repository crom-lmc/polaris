---
description: Polaris 前端 L2 规范，编辑 polaris-web/** 时加载。约束 API 解包、权限、表格、主题、响应式、SSO 回调与构建验证。
globs: ["polaris-web/**"]
alwaysApply: false
level: L2
---

# 前端 L2 规范（polaris-web/**）

> 11 个独立应用口径不一致是常态；先确认本应用写法再动手。L1 红线见 `00-L1-redlines.md`。

## 先确认本应用口径

- MUST 先核实本应用的响应解包（A：直接 `body.data` / B：sso、audit 需 `{data:T}` / C：forum-web fetch）、401 行为、token localStorage 键名、是否已有 `ft-*` 与 `useSessionGuard`。
- MUST NOT 把 job 的 `quoteLongIntegers()` 转换绕过（19 位 ID 精度）；MUST NOT 跨应用照抄 API 层泛型。
- MUST 请求集中在 `src/api/`，`baseURL:''` + `/api` 代理，`withCredentials:true` 保留中心会话；错误对象业务码优先、HTTP 码兜底。
- **提交给 `LocalDateTime` 的时间**：MUST 用 ISO 8601（`dayjs(...).format('YYYY-MM-DDTHH:mm:ss')`，日期与时间之间是 `T`），MUST NOT 提交空格分隔的 `YYYY-MM-DD HH:mm:ss`——Jackson 默认只认 `T`，空格会直接导致 `HttpMessageNotReadableException`；后端返回的时间串展示时再 `replace('T', ' ')`。新建/修改预定、延时曾因此整批提交失败。
- **顶栏用户名展示**：MUST 展示中文昵称（JWT payload 的 `nick` claim），MUST NOT 直接展示登录名/英文拼音。网关按约定不注入中文头（`X-Account-Name` 是登录名），下游 `/me` 若也把它当姓名返回，前端须自行从 accessToken 解出 `nick` 作为 `displayName`，失败再回退登录名；解析只用于展示、不参与鉴权。

## 组件、权限与路由

- MUST 使用 `<script setup lang="ts">`；权限用 `store.can()` / job `hasPerm()` / 路由 `meta.perm`，三者与后端 `@Permit` 同一字符串。
- **侧栏菜单口径**：业务应用侧栏的**名称、顺序、层级 MUST 来自 SSO 权限树**（`sso_permission` 中 `type=1` 菜单节点，经 `GET /api/auth/me?app=<code>` 的 `menus` 下发：`code/name/sort/parentCode`）。前端 MUST 只保留「权限码 → 路由/图标/短名」映射，MUST NOT 在 Layout 里硬编码菜单标题或排序数组；`store.can()` 只负责显隐。改名/调序/新增菜单先在 SSO 侧（`PermissionConstants.*_BUILTIN` + `deploy/sql` 或管理端）维护，前端仅同步路由映射。同一 `sort` 保持后端返回顺序，MUST NOT 按权限码字母重排。
- **物料选择口径**：选物料 MUST 用 `_shared/components/MaterialPickerModal.vue`（左分类树按需展开、右分页搜索、单选/多选），MUST NOT 用一次性拉全量物料树的 `a-select` 下拉；调用方只提供 `loadChildren/loadMaterials` 适配器并接收完整物料对象（code/name/spec/unit/price）。
- MUST NOT 新增 `v-perm` 指令；sso 审批/消息中心 MUST NOT 挂 `meta.perm`（会挡住普通员工）。
- MUST 带 base 应用保持三件套一致：vite `base` / `createWebHistory(base)` / callback 路径（forum-admin `/admin/`、portal `/portal/`）。
- **应用图标一致**：MUST 每个应用的 `public/favicon.svg` 与 `_shared/AppCenterNav.vue` 中该 `code` 对应的 `public/icons/<code>.svg`（发布在 SSO 静态目录）保持同一图形与配色；MUST NOT 遗留从别的应用复制来的 favicon（meeting 曾误用 file 服务的蓝色文件夹图标）。部署时给 `favicon.svg` 加 `Cache-Control: no-cache`，避免浏览器长期缓存旧图标。
- MUST 路由兜底 `/:pathMatch(.*)*`，无权限页用 `a-result status="403"` 且标 public。
- **枚举值口径**：MUST 前端状态比较与提交值同后端常量、字典 `value` 完全一致（如会议室状态后端/字典均为中文「可用/维护/停用」）；MUST NOT 在前端另造一套英文编码。若历史数据已写入错误编码，展示层做归一映射并补数据修正脚本（meeting 会议室维护页曾因此返工）。

## 表格与交互

**列表页骨架**：MUST 用 `<section class="ft-page"><section class="ft-head">…<section class="ft-scroll ft-bottom">`；`ft-scroll` 是唯一滚动容器，`ft-bottom` 让分页 margin:auto 贴底 + sticky bottom:0（z-index 5）。

**页面留白（header 搜索栏与卡片间距）**：全局 `.ft-head` 仅 `flex:0 0 auto`、无间距，header 内的标题/筛选控件之间及 header 与下方卡片之间 MUST 显式留间距（约 12px）。统一给 `ft-head` 加 `.toolbar` 类并定义 `.toolbar { display:flex; align-items:center; gap:12px; margin-bottom:12px; flex-wrap:wrap; }`，标题与筛选组之间用 1px 竖线分隔条或 `margin-left`。MUST NOT 把标题与 `a-space` 控件直接塞进无样式 `ft-head` 导致全部贴在一起（meeting 使用率页曾返工）。图表页同样适用。

**分页与日期筛选口径**：
- MUST 列表默认 `pageSize: 20`、开 `showSizeChanger`；MUST NOT 写死 5 或更小的每页条数（meeting 爽约记录页曾写死 5 被要求改）。换每页条数时回到第 1 页，翻页/换 size 走同一 `@change` 入口。
- MUST 查询用的 `a-range-picker` 允许清空（`allow-clear` 默认开，MUST NOT 写 `:allow-clear="false"`），清空即「不限日期」——前端传空 from/to，后端不得兜底回「最近 7 天」；默认值可用最近一周，但用户清空后必须真正查全部（meeting 爽约记录页曾前后端同时踩坑）。

**空数据铺满 + 分页固定（三条必须同时落地）**：
- MUST 在 `a-table` 上绑定空态触发类 `:class="{ 'tbl-empty-mode': !loading && !rows.length }"`（`rows` 为本页数据数组名），并在 `#emptyText` 插槽用 `<div class="tbl-empty"><a-empty v-if="!loading" description="…" /></div>`。
- MUST 在本应用 `src/styles/global.css`（或页面 `<style scoped>` 用 `:deep()`）定义 `.tbl-empty-mode` 表格链：`.ant-table-container`/`.ant-table-content` 设 `flex:1 0 auto; display:flex; flex-direction:column`，`.ant-table-content > table` 设 `height:100%`，`.ant-table-placeholder > td` 设 `height:100%; padding:0`，`.tbl-empty` 设 `height:100%; min-height:240px; display:flex; align-items:center; justify-content:center`。
- MUST NOT 只写 `#emptyText` 插槽却漏掉 `.tbl-empty-mode` 触发类或 `.tbl-empty` 样式定义——空态会只占一行高、下方大片留白（meeting 会议室维护页曾有此问题）。有分页的页面 MUST 用 `ft-bottom`（分页 sticky 右下角、z-index 5 高于操作列 3）；无分页页同样加空态铺满，但无需分页条。
- **空态不出现横向滚动条**：MUST 把固定横向滚动改为按数据条件绑定，如 `:scroll="rows.length ? { x: 1080 } : undefined"`（无数据时传 `undefined`，不要传固定 `{ x }`）；空态 CSS 再兜底 `.tbl-empty-mode .ant-table-content { overflow-x: hidden }`。MUST NOT 在零数据时空表仍显示横向滚动条（meeting 我的预定页曾因此返工）。
- **树 + 表布局变体（合同物料库/物料维护）**：左侧分类树 + 右侧表格的页面，右侧 MUST 用 `.panel-tree-main.ft-bottom` 包裹卡片（`.tree-table-card`），表格链 `flex:1 0 auto` 撑满卡片、`a-card-body` 设 `padding:0` 且 `flex:1 1 auto`，分页用 `.panel-tree-main.ft-bottom .tree-table-card .ant-table-pagination.ant-pagination` 做 `position:sticky; bottom:0; z-index:5; margin:auto 0 0` 贴底。MUST NOT 给这类页面写 `:pagination="false"` 让列表不分页、或让表格只占内容高度留大片空白（2026-11-27 物料维护页曾因此被要求整改）。
- **置灰操作按钮只置灰、不小一号（合同/财务所有模块统一，MUST）**：禁用的链接式操作按钮（删除/编辑）MUST 用 `.link-disabled { color: var(--muted); cursor: not-allowed }`，只改颜色与光标、不设 `font-size`，继承 `<a>` 默认字号与相邻操作一致；MUST NOT 用 `.muted`（其 `font-size:12px` 会让置灰按钮比正常「编辑/停用」小一号，2026-11-27 物料库删除按钮曾因此被要求整改），也 MUST NOT 用缩小字号的 disabled 样式。禁用原因用 `a-tooltip` hover 展示，不在按钮旁常驻文字。
- **列表页常驻说明提示一律去掉（合同/财务所有模块统一，MUST）**：列表页 MUST NOT 放常驻的说明性 `a-alert`（如物料库/物料维护底部「维护口径」长提示）、顶部 `hint` 编码说明、左侧「共 N 个分类/条」计数提示；页面只保留标题、搜索栏、操作按钮与表格。禁用原因、编码规则、字段来源等解释只在 hover（`a-tooltip`/`title`）或弹窗中呈现，不在页面常驻（2026-11-27 物料库曾因此被要求整改）。
- **表单标签左对齐、必填星号不挤文字（合同/财务所有模块统一，MUST）**：表单（含抽屉/弹窗）字段标签文字 MUST 左对齐，必填项与非必填项的标签文字起点 MUST 在同一竖线上；必填星号 MUST NOT 把文字推右。AntDV 4 `required` 的 `label::before` 是 `inline-block + margin-inline-end` 会占位推字，两个应用 `src/styles/global.css` MUST 统一覆盖（选择器 MUST 带 `.ant-form .ant-form-item` 前缀提高特异性，压过 CSS-in-JS 运行时 `:not()` 规则）：`.ant-form .ant-form-item .ant-form-item-label > label { padding-left: 14px }` + `.ant-form .ant-form-item .ant-form-item-label > label.ant-form-item-required::before { position:absolute; left:0; top:50%; transform:translateY(-50%); margin-inline-end:0 }`，星号绝对定位落在左侧预留区、不参与文字流。MUST NOT 让必填标签文字因星号与非必填标签错位（2026-11-27 客户/供应商新增抽屉曾因此被要求整改）。

**展示兜底（时间/姓名 ID）**：
- MUST 列表里的时间（会议时间、释放时间等跨天/跨年数据）展示带年份 `YYYY-MM-DD HH:mm`，MUST NOT 只显示 `MM-DD HH:mm` 导致跨年无法区分；列宽按完整区间（含年份）预留并同步 `:scroll.x`。
- MUST 后端姓名映射查不到账号时可能回退为 ID 本身（如 meeting `employeeNames` 的兜底），前端展示姓名列 MUST 把「等于账号 ID 的纯数字」归一为 `-`，MUST NOT 把雪花 ID 暴露给用户（tooltip 用同一归一值）。

**长文本列（省略 + 悬浮展示完整内容）**：

- MUST 用全局 `.cell-ellipsis`（定义于 `_shared/styles/responsive.css`：`display:block; max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap`）让文本在列宽内以省略号收尾，外层用 `<a-tooltip :title="完整值">` 在鼠标悬浮时以浮层展示完整内容——即「显示省略、悬浮看全」。
- MUST 标准形态：`<a-tooltip :title="record.xxx"><span class="cell-ellipsis">{{ record.xxx }}</span></a-tooltip>`；值为空时用 `v-if` + `v-else` 显示 `-` 兜底。
- MUST tooltip 的 title 绑定与被省略内容**同一个字段**；MUST NOT 显示 A 字段却悬浮展示 B 字段。
- MUST NOT 用 JS `slice()` 硬截断长文本（会丢失完整信息），MUST NOT 用原生 `title` 属性代替 `a-tooltip`（样式、延迟、移动端表现不一致）。在 flex 行内使用时，MUST 补 `flex:1 1 auto; min-width:0` 让省略号生效。
- 堆栈、入参/响应等超长内容用「行内省略 + 展开行 `<pre>` 看全文」双层策略，不把全文塞进单元格。

**列表标题（合同 / 财务所有模块统一）**：
- MUST NOT 在表格上方添加由当前选中节点、筛选条件等拼出来的**额外动态标题**（如 `` `子分类：${currentNode.name}` ``、`` `物料清单：${currentNode.name}` ``）。`a-card` / 表格容器直接用静态标题或不加标题；页面名和选中项已在侧栏、树节点或筛选控件中体现时，MUST NOT 再拼一段重复标题。静态区块标题（如「合同信息」「基本信息」）保留。
- **`ft-head` 工具栏不重复放页面标题与右侧说明（合同 / 财务 / 采购计划所有模块统一，MUST）**：`ft-head.toolbar` MUST NOT 放 `<h2>`（或等价的独立页面标题）以及紧随其后的 `divider`/`panel-divider`——页面名已由侧栏菜单或页签表达；工具栏右侧 MUST NOT 放 `class="hint"` / `panel-hint` 常驻说明（如「审批通过后转采购合同并带入清单」「项目独立维护；所属项目在合同侧选择」）。工具栏只保留筛选控件、`grow` 留白与操作按钮；表单/卡片内的静态区块标题（`a-card title`、分组标题）不受此限。`polaris-design-web` 设计稿中的对应页面同步适用（2026-11-27 采购计划截图反馈：左侧标题与右侧提示需去掉）。
- MUST 表头单行不折叠：两个应用 `src/styles/global.css` 对 `.ant-table-thead > tr > th` 及 `.ant-table-column-title` 设 `white-space: nowrap !important`；同时把「列宽合计 > `scroll.x`」的表格加宽 `scroll.x` 与 `--ft-min-width`，MUST NOT 只加 nowrap 不加宽导致列被挤压。

**操作列固定**：MUST 用 `.ft-table` + CSS `position:sticky; right:0; z-index:3` 固定最后一列；MUST NOT 用 antd 的 `fixed:'right'`（会在表格内部再开一层横向滚动，与整页滚动容器冲突；审计 `EventsView.vue` 是历史例外）。按钮左对齐不换行，靠 `.ft-table .ant-btn-link` 的 padding 规则实现。

**列表字段类型归一化**：后端实体用逗号分隔 String 存储、而提交 DTO 是 `List` 的字段（如会议室 `fileIds`），前端 TS 类型 MUST 写成 `string[] | string`，回填与提交 MUST 经统一函数归一为数组（按逗号 split + trim + 过滤空值），MUST NOT 用 `record.xxx || []` 直接回填——字符串会被原样提交，Jackson 反序列化 `ArrayList` 失败报「系统异常」（meeting 编辑会议室曾因此报错）。

**内容多的表单用抽屉**：表单字段超过一屏、或含上传/多段列表/时间轴等长内容时，MUST 用 `a-drawer`（`placement="right"` + `#footer` 放取消/确定）而非 `a-modal`，并给 body 留底部 padding 避免内容被 footer 遮挡（meeting 编辑会议室弹窗曾一屏放不下）。简单确认/详情弹窗仍可用 `a-modal`。移动端抽屉宽度用 `100%`，表单栅格降为两列，MUST NOT 沿用固定宽度的 PC 弹窗导致开始时间等字段被裁到屏幕外（meeting 修改预定曾如此）。

**移动端 48 格时间轴**：MUST 拆成 `00:00–12:00`、`12:00–24:00` 两行，每行 24 格 + 半日小时标尺，时间范围表单的开始/结束仍要能独立用下拉修改。修改时段统一用两点选择：第一格直接作为新起点，第二格**计入会议**并以其**终点**作为结束时间（点 17:00–17:30 格 → 结束 17:30），PC/移动端口径一致；第二格若被他人/本人其他预定占用则不可点，区间内占用仍要拦截；MUST NOT 取第二格起点导致点 17:00–17:30 只得到 17:00，或沿用原区间只延长结束（meeting 修改预定曾返工三次）。

**搜索型下拉的默认数据**：邀请参会人等可搜索下拉 MUST 在空关键词时也请求默认列表（如 meeting `searchEmployees('')`），并把已选项置顶合并；MUST NOT 只在用户输入后才出数据、或只回显已选 ID 导致「暂无数据」误解（meeting 修改预定曾如此）。

**查询类日期范围默认一周**：MUST 搜索/筛选/统计用途的 `a-range-picker`（含弹窗内统计范围）初始值、重置值均为「含今天的最近 7 个自然日」——`[dayjs().subtract(6,'day').startOf('day'), dayjs()]`（字符串口径 `format('YYYY-MM-DD')`），首次进入就有数据、不需手选；MUST NOT 默认空值/`null`/仅今天。仅「表单录入类」日期范围（请假申请、教育/工作经历、合同）与按月统计（`picker="month"`）可保留自定义默认或空值。

**单日期统计过滤**：概览类单日期过滤 MUST 默认今天，可选范围 `[今天, 今天 + 配置的最大可预约天数]`（如埋点 `meeting.booking.max_advance_days`），MUST NOT 允许选今天之前的日期；统计卡 MUST 与所选日期同步刷新，并在 UI 明示数据范围（全平台/本人）。

**页签角标与统计口径**：MUST 页签上的数量角标与列表/统计卡使用完全相同的筛选条件（城市、状态、关键词、日期范围等），并在任一筛选变化时与列表一起刷新；MUST NOT 角标用全表总数而列表按范围过滤——否则筛选范围内无数据时角标仍显示旧数字（meeting 我的预定页曾因此返工）。

**其余**：MUST 表头 `align` 与列内容一致（数字右、状态居中、文本左）；确认框用 `centered:true`；抽屉内嵌表格用 `.drawer-body-flex`；禁用按钮配合 `a-tooltip` 说明原因。label 与 hint 之间用 `&nbsp;`。

## 主题与响应式

- MUST 颜色走 CSS 变量（`:root` 与 `:root[data-theme='dark']` 两套），共享组件 `--primary` 用 `var(--primary, #2f54eb)` 兜底。
- MUST `index.html` 首屏内联脚本设 `data-theme`，优先级 cookie `polaris_theme` > 本应用 `<app>_theme` > `prefers-color-scheme`。
- MUST 后台应用在自身 CSS 之后引入 `@shared/styles/responsive.css` 并使用 `useResponsiveCollapsed()`；MUST NOT 自写一套媒体查询。
- MUST 浅色/深色两套主题、PC/Pad 两种宽度都验证过。

## SSO 与应用中心

- MUST 应用中心签码先同步开窗，再 `POST /api/auth/oauth/authorize`；callback 用注册的完整 `redirect_uri`，state 与 `expired=1` 分支保留。
- MUST 回调页必须选一种 state 校验口径（多数仅 warn、forum 系阻断），并过滤 redirect 不以 `/callback` 开头防自循环。
- MUST 门户/论坛前台 `v-if="store.isLoggedIn"` 才渲染应用中心；正式应用名（含应用中心卡片、布局标题、浏览器标签、favicon 无障碍名）从 `sso_application.name`（`/api/accounts/me/apps`）动态取，MUST NOT 在 `AppCenterNav.vue` 的 `ALL_APPS` 里写死展示名；接口失败才用兜底名。
- **应用中心授权过滤**：MUST 只展示 `/api/accounts/me/apps` 返回的已授权应用，外加全员应用 `portal`/`forum`/`meeting` 与当前应用；接口失败时收敛为「全员应用 + 当前应用」，MUST NOT 把 `ALL_APPS` 全量写死展示（谢强强仅 5 个应用却在应用中心看到 12 个曾造成误解）；`file` 北极星云非全员应用，MUST NOT 加入 `PUBLIC_APP_CODES`。
- MUST 全局登出调用 `/api/auth/logout` 作废旧 token，MUST NOT 只清 localStorage。

## 附件上传

- MUST 用 `_shared/components/AttachmentUpload.vue` 统一上传入口，业务侧只提供 `upload(file)` 与 `fileIds` v-model；共享组件不 import ant-design-vue 等应用依赖，错误经 `error` 事件冒泡。
- MUST 按独立按钮权限码显示上传入口，MUST NOT 直接复用新增/编辑权限；列表新增附件列时同步加宽 `scroll.x` 与 `--ft-min-width`。

## 闭环字段完整性

- MUST 设计稿画出的字段全部有录入与展示，不因流程闭环而省字段；长文本 `.cell-ellipsis` + `a-tooltip`。
- MUST 字典类下拉走公共字典 `fetchPublicDict(typeCode)`，新增字典先在 settings `PublicReferenceController.DICT_ALLOWED` 放开；凭证状态机按 DRAFT/REVIEW/POSTED 给 提交审核/审核/过账 操作。

## 工程与交付

- MUST 新文件换行符与周边一致（polaris-web 源文件为 CRLF，`_shared/components` 为 LF/无 BOM），MUST NOT 破坏带 BOM 的文件。
- MUST 新增监听器在 `onBeforeUnmount` 成对清理。
- MUST 通过 `vue-tsc --noEmit` + `vite build`；改 `_shared/` 时 MUST 全部 11 个应用验证。
- MUST 最终回复列明需发布的静态资源；改 `_shared/` 发 11 个应用，单应用改动只发该应用。
- **SPA 发布缓存策略**：MUST 在站点 Nginx 对入口 `/index.html`（含 `/`）返回 `Cache-Control: no-cache, no-store, must-revalidate`，对带 hash 的 `/assets/` 设 `expires 1y` + `Cache-Control: public, immutable`；MUST NOT 让入口 HTML 被浏览器启发式缓存——否则发版后旧入口仍引用已删除的 hash 资源，动态 import 会报 “Expected a JavaScript module script but the server responded with a MIME type of text/html”。已缓存旧入口的浏览器发布后需硬刷新一次。
- **增量发布不清 hash 资源**：MUST NOT 用 `rsync --delete` 之类方式同步 `dist/`——旧页面/后台标签仍会请求上一版 hash 资源，删掉后报 `Failed to fetch dynamically imported module … 404`。MUST 只覆盖新增/变更文件，旧 hash 资源保留至少 7 天后再按 mtime 清理（meeting 已出现两次此问题）。
