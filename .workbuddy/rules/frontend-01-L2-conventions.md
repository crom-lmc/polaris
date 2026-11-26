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
- MUST NOT 新增 `v-perm` 指令；sso 审批/消息中心 MUST NOT 挂 `meta.perm`（会挡住普通员工）。
- MUST 带 base 应用保持三件套一致：vite `base` / `createWebHistory(base)` / callback 路径（forum-admin `/admin/`、portal `/portal/`）。
- **应用图标一致**：MUST 每个应用的 `public/favicon.svg` 与 `_shared/AppCenterNav.vue` 中该 `code` 对应的 `public/icons/<code>.svg`（发布在 SSO 静态目录）保持同一图形与配色；MUST NOT 遗留从别的应用复制来的 favicon（meeting 曾误用 file 服务的蓝色文件夹图标）。部署时给 `favicon.svg` 加 `Cache-Control: no-cache`，避免浏览器长期缓存旧图标。
- MUST 路由兜底 `/:pathMatch(.*)*`，无权限页用 `a-result status="403"` 且标 public。
- **枚举值口径**：MUST 前端状态比较与提交值同后端常量、字典 `value` 完全一致（如会议室状态后端/字典均为中文「可用/维护/停用」）；MUST NOT 在前端另造一套英文编码。若历史数据已写入错误编码，展示层做归一映射并补数据修正脚本（meeting 会议室维护页曾因此返工）。

## 表格与交互

**列表页骨架**：MUST 用 `<section class="ft-page"><section class="ft-head">…<section class="ft-scroll ft-bottom">`；`ft-scroll` 是唯一滚动容器，`ft-bottom` 让分页 margin:auto 贴底 + sticky bottom:0（z-index 5）。

**分页与日期筛选口径**：
- MUST 列表默认 `pageSize: 20`、开 `showSizeChanger`；MUST NOT 写死 5 或更小的每页条数（meeting 爽约记录页曾写死 5 被要求改）。换每页条数时回到第 1 页，翻页/换 size 走同一 `@change` 入口。
- MUST 查询用的 `a-range-picker` 允许清空（`allow-clear` 默认开，MUST NOT 写 `:allow-clear="false"`），清空即「不限日期」——前端传空 from/to，后端不得兜底回「最近 7 天」；默认值可用最近一周，但用户清空后必须真正查全部（meeting 爽约记录页曾前后端同时踩坑）。

**空数据铺满 + 分页固定（三条必须同时落地）**：
- MUST 在 `a-table` 上绑定空态触发类 `:class="{ 'tbl-empty-mode': !loading && !rows.length }"`（`rows` 为本页数据数组名），并在 `#emptyText` 插槽用 `<div class="tbl-empty"><a-empty v-if="!loading" description="…" /></div>`。
- MUST 在本应用 `src/styles/global.css`（或页面 `<style scoped>` 用 `:deep()`）定义 `.tbl-empty-mode` 表格链：`.ant-table-container`/`.ant-table-content` 设 `flex:1 0 auto; display:flex; flex-direction:column`，`.ant-table-content > table` 设 `height:100%`，`.ant-table-placeholder > td` 设 `height:100%; padding:0`，`.tbl-empty` 设 `height:100%; min-height:240px; display:flex; align-items:center; justify-content:center`。
- MUST NOT 只写 `#emptyText` 插槽却漏掉 `.tbl-empty-mode` 触发类或 `.tbl-empty` 样式定义——空态会只占一行高、下方大片留白（meeting 会议室维护页曾有此问题）。有分页的页面 MUST 用 `ft-bottom`（分页 sticky 右下角、z-index 5 高于操作列 3）；无分页页同样加空态铺满，但无需分页条。
- **空态不出现横向滚动条**：MUST 把固定横向滚动改为按数据条件绑定，如 `:scroll="rows.length ? { x: 1080 } : undefined"`（无数据时传 `undefined`，不要传固定 `{ x }`）；空态 CSS 再兜底 `.tbl-empty-mode .ant-table-content { overflow-x: hidden }`。MUST NOT 在零数据时空表仍显示横向滚动条（meeting 我的预定页曾因此返工）。

**长文本列（省略 + 悬浮展示完整内容）**：

- MUST 用全局 `.cell-ellipsis`（定义于 `_shared/styles/responsive.css`：`display:block; max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap`）让文本在列宽内以省略号收尾，外层用 `<a-tooltip :title="完整值">` 在鼠标悬浮时以浮层展示完整内容——即「显示省略、悬浮看全」。
- MUST 标准形态：`<a-tooltip :title="record.xxx"><span class="cell-ellipsis">{{ record.xxx }}</span></a-tooltip>`；值为空时用 `v-if` + `v-else` 显示 `-` 兜底。
- MUST tooltip 的 title 绑定与被省略内容**同一个字段**；MUST NOT 显示 A 字段却悬浮展示 B 字段。
- MUST NOT 用 JS `slice()` 硬截断长文本（会丢失完整信息），MUST NOT 用原生 `title` 属性代替 `a-tooltip`（样式、延迟、移动端表现不一致）。在 flex 行内使用时，MUST 补 `flex:1 1 auto; min-width:0` 让省略号生效。
- 堆栈、入参/响应等超长内容用「行内省略 + 展开行 `<pre>` 看全文」双层策略，不把全文塞进单元格。

**操作列固定**：MUST 用 `.ft-table` + CSS `position:sticky; right:0; z-index:3` 固定最后一列；MUST NOT 用 antd 的 `fixed:'right'`（会在表格内部再开一层横向滚动，与整页滚动容器冲突；审计 `EventsView.vue` 是历史例外）。按钮左对齐不换行，靠 `.ft-table .ant-btn-link` 的 padding 规则实现。

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

## 工程与交付

- MUST 新文件换行符与周边一致（polaris-web 源文件为 CRLF），MUST NOT 破坏带 BOM 的文件。
- MUST 新增监听器在 `onBeforeUnmount` 成对清理。
- MUST 通过 `vue-tsc --noEmit` + `vite build`；改 `_shared/` 时 MUST 全部 11 个应用验证。
- MUST 最终回复列明需发布的静态资源；改 `_shared/` 发 11 个应用，单应用改动只发该应用。
- **SPA 发布缓存策略**：MUST 在站点 Nginx 对入口 `/index.html`（含 `/`）返回 `Cache-Control: no-cache, no-store, must-revalidate`，对带 hash 的 `/assets/` 设 `expires 1y` + `Cache-Control: public, immutable`；MUST NOT 让入口 HTML 被浏览器启发式缓存——否则发版后旧入口仍引用已删除的 hash 资源，动态 import 会报 “Expected a JavaScript module script but the server responded with a MIME type of text/html”。已缓存旧入口的浏览器发布后需硬刷新一次。
- **增量发布不清 hash 资源**：MUST NOT 用 `rsync --delete` 之类方式同步 `dist/`——旧页面/后台标签仍会请求上一版 hash 资源，删掉后报 `Failed to fetch dynamically imported module … 404`。MUST 只覆盖新增/变更文件，旧 hash 资源保留至少 7 天后再按 mtime 清理（meeting 已出现两次此问题）。
