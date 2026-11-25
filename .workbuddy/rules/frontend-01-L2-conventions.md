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

## 组件、权限与路由

- MUST 使用 `<script setup lang="ts">`；权限用 `store.can()` / job `hasPerm()` / 路由 `meta.perm`，三者与后端 `@Permit` 同一字符串。
- MUST NOT 新增 `v-perm` 指令；sso 审批/消息中心 MUST NOT 挂 `meta.perm`（会挡住普通员工）。
- MUST 带 base 应用保持三件套一致：vite `base` / `createWebHistory(base)` / callback 路径（forum-admin `/admin/`、portal `/portal/`）。
- MUST 路由兜底 `/:pathMatch(.*)*`，无权限页用 `a-result status="403"` 且标 public。

## 表格与交互

**列表页骨架**：MUST 用 `<section class="ft-page"><section class="ft-head">…<section class="ft-scroll ft-bottom">`；`ft-scroll` 是唯一滚动容器，`ft-bottom` 让分页 margin:auto 贴底 + sticky bottom:0（z-index 5）。空数据用 `a-table` 的 `#emptyText` 插槽 + `.tbl-empty` 铺满内容区。

**长文本列（省略 + 悬浮展示完整内容）**：

- MUST 用全局 `.cell-ellipsis`（定义于 `_shared/styles/responsive.css`：`display:block; max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap`）让文本在列宽内以省略号收尾，外层用 `<a-tooltip :title="完整值">` 在鼠标悬浮时以浮层展示完整内容——即「显示省略、悬浮看全」。
- MUST 标准形态：`<a-tooltip :title="record.xxx"><span class="cell-ellipsis">{{ record.xxx }}</span></a-tooltip>`；值为空时用 `v-if` + `v-else` 显示 `-` 兜底。
- MUST tooltip 的 title 绑定与被省略内容**同一个字段**；MUST NOT 显示 A 字段却悬浮展示 B 字段。
- MUST NOT 用 JS `slice()` 硬截断长文本（会丢失完整信息），MUST NOT 用原生 `title` 属性代替 `a-tooltip`（样式、延迟、移动端表现不一致）。在 flex 行内使用时，MUST 补 `flex:1 1 auto; min-width:0` 让省略号生效。
- 堆栈、入参/响应等超长内容用「行内省略 + 展开行 `<pre>` 看全文」双层策略，不把全文塞进单元格。

**操作列固定**：MUST 用 `.ft-table` + CSS `position:sticky; right:0; z-index:3` 固定最后一列；MUST NOT 用 antd 的 `fixed:'right'`（会在表格内部再开一层横向滚动，与整页滚动容器冲突；审计 `EventsView.vue` 是历史例外）。按钮左对齐不换行，靠 `.ft-table .ant-btn-link` 的 padding 规则实现。

**其余**：MUST 表头 `align` 与列内容一致（数字右、状态居中、文本左）；确认框用 `centered:true`；抽屉内嵌表格用 `.drawer-body-flex`；禁用按钮配合 `a-tooltip` 说明原因。label 与 hint 之间用 `&nbsp;`。

## 主题与响应式

- MUST 颜色走 CSS 变量（`:root` 与 `:root[data-theme='dark']` 两套），共享组件 `--primary` 用 `var(--primary, #2f54eb)` 兜底。
- MUST `index.html` 首屏内联脚本设 `data-theme`，优先级 cookie `polaris_theme` > 本应用 `<app>_theme` > `prefers-color-scheme`。
- MUST 后台应用在自身 CSS 之后引入 `@shared/styles/responsive.css` 并使用 `useResponsiveCollapsed()`；MUST NOT 自写一套媒体查询。
- MUST 浅色/深色两套主题、PC/Pad 两种宽度都验证过。

## SSO 与应用中心

- MUST 应用中心签码先同步开窗，再 `POST /api/auth/oauth/authorize`；callback 用注册的完整 `redirect_uri`，state 与 `expired=1` 分支保留。
- MUST 回调页必须选一种 state 校验口径（多数仅 warn、forum 系阻断），并过滤 redirect 不以 `/callback` 开头防自循环。
- MUST 门户/论坛前台 `v-if="store.isLoggedIn"` 才渲染应用中心；正式应用名从 `sso_application.name` 动态取。
- MUST 全局登出调用 `/api/auth/logout` 作废旧 token，MUST NOT 只清 localStorage。

## 工程与交付

- MUST 新文件换行符与周边一致（polaris-web 源文件为 CRLF），MUST NOT 破坏带 BOM 的文件。
- MUST 新增监听器在 `onBeforeUnmount` 成对清理。
- MUST 通过 `vue-tsc --noEmit` + `vite build`；改 `_shared/` 时 MUST 全部 11 个应用验证。
- MUST 最终回复列明需发布的静态资源；改 `_shared/` 发 11 个应用，单应用改动只发该应用。
