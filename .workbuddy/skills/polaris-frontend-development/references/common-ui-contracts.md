# 前端公共代码实证规范（从代码提炼）

> 所有路径相对 `polaris-web/`。【共性】【例外】【坑】标注来源为实际代码或源码注释。
> 本文件是代码事实的提炼；与代码冲突时以代码为准。

## 1. `_shared/` 三件套的真实 API

- 【共性】`_shared/AppCenterNav.vue`：唯一 prop 是 `currentCode`（`defineProps<{ currentCode: string }>()`）；内置 `ALL_APPS` 11 项，字段固定 `code/name/desc/icon/mark/bg/fg/url/callback`；分组 `GROUP_DEFS` 为 SYSTEM「太空基地」/ MANAGEMENT「指挥调度」/ PORTAL「星辰生活区」，标题通过 `GET /api/settings/public/dicts/sso.application_type` 在线覆盖（失败保留兜底）。
- 【共性】签码 `enter(app)`：先**同步** `window.open('about:blank','_blank')` 防弹窗拦截，再 `POST /api/auth/oauth/authorize`（`responseType:'code'`、`clientId`、`redirectUri`、`state`，`credentials:'include'`）；token 用 `/access.?token$/i` 正则扫 localStorage，不写死键名；state 用 `crypto.getRandomValues` 生成并存 `sessionStorage`。
- 【坑】`callback` 必须是注册在 `sso_application.redirect_uri` 的完整地址；送首页会拿不到 `code`。`sso-center` 例外，不签码直接开首页。
- 【共性】`_shared/useResponsiveCollapsed.ts`：`useResponsiveCollapsed(query='(max-width: 1024px)'): Ref<boolean>`，首屏 `matchMedia` 初始化、`change` 跟随、点击 `.sider` 内 `.ant-menu-item` 才收起（忽略 `.menu-toggle`/`.ac-panel`），`onBeforeUnmount` 清监听。**未发现有调用点传自定义 query**，不要自己另写断点逻辑。
- 【共性】`_shared/styles/responsive.css`：1024/768/640/480 四档；提供 `.cell-ellipsis`（权威定义在第 32–38 行）、`.shell` 防溢出骨架、各类 grid 降级类；弹层类不受 `.shell` 限定。引入顺序必须是**应用自身 global.css/theme.css 之后**；forum-web / portal-web 不引入。
- 【坑】`useResponsiveCollapsed` 强耦合 `.sider/.menu-toggle/.ant-menu-item` 类名，改名会使抽屉点击逻辑失效。

## 2. API 层三套解包口径与 401 行为

| 口径 | 应用 | 拦截器返回 | 调用方写法 |
|---|---|---|---|
| A | settings/cms/ehr/file/job/openapi/forum-admin | `body.data` | `request.get<never, T>()` 直接拿数据 |
| B | **sso / audit** | `{ ...response, data: body.data }` | `request.get<never, { data: T }>()`，需 `.data` |
| C | forum-web | 原生 fetch 薄封装 `request<T>` | 直接拿数据 |

- 【共性】axios 版统一：`baseURL:''`（走 Vite/Nginx `/api` 代理）、`withCredentials:true`（HttpOnly `SSO_SID` 承载中心会话）、请求拦截器注入 `Bearer <token>`、响应 `code !== 0` 时 reject。
- 【共性】错误对象统一 `Error & { code?: number; status?: number }`，`err.code` 取业务码优先、HTTP 码兜底；**不能让 HTTP 状态码覆盖 4xx 响应体里的业务码**。
- 【共性】超时：sso/audit 20s，其余业务应用 30s。
- 【例外】401 行为：sso → `/login?redirect=`；audit → `gotoAuthorize`；cms/ehr/file/settings/openapi/job → `/callback?redirect=…&expired=1`；forum-admin → `/admin/callback`；**portal 不跳登录不清 token**；forum-web 派发 `forum:unauthorized` 事件再跳 SSO。
- 【坑】`skipAuthRedirect` 专供 `/api/auth/session` 等探测接口，否则"令牌有效但中心会话 cookie 失效"会被反复踢回登录页；sso/audit 显式声明模块增强，其余应用只在拦截器读该属性。
- 【坑】job 的 `transformResponse` + `quoteLongIntegers()` 把 16 位以上整数字面量加引号防雪花 ID 精度丢失，**仅此一处实现**，新代码不要绕过它。
- 【坑】file-web 的 `shareDownload()` 另有一处裸 fetch 用于读 `content-disposition`，不属于 axios 封装。

## 3. user / theme store 的公共接口与差异

- 【共性】8 个业务后台的 user store 导出 `ready`、`bootstrap(): Promise<boolean>`、`can(code)`、`clear()/logout()`、`applyMe()`；`bootstrap()` 无 token 直接 false，有 token 拉 `fetchMe()`，catch 内清 token **不抛异常**（守卫据此跳登录）；`can()` 超管直通，与后端 `@Permit` 同口径。
- 【例外】sso/audit：多 `applyTokens/applySession/loadAppList`，支持"本地 token → `me()`，否则 `sessionToken(true)` 中心会话静默换票"两级回退，只有 `clear` 没有 `logout`。
- 【例外】ehr 多 `canAny(...codes)` 配 `meta.perms`；portal 只有 `isLoggedIn`，不强制登录；forum-admin catch 内额外 `me.value=null`；job 用 Options API，方法是 `ensureLogin/hasPerm/gotoAuthorize/exchangeToken`，权限身份存 localStorage；forum-web 没有 `stores/user.ts`，改用 `src/auth.ts`。
- 【共性】theme store（10 个 Pinia 版）：优先级 共享 cookie `polaris_theme` → 本应用 `<app>_theme` → `prefers-color-scheme` → light；cookie `SameSite=Lax`、≥3 段主机名写主域；`applyDom` 同步 `data-theme`；`window focus` 跨应用同步；导出 `mode/antTheme/setMode/toggle`。`index.html` 11 个应用全部内联首屏脚本做同样优先级。
- 【坑】openapi 的 `STORAGE_KEY='settings_theme'`、`token.ts` 用 `settings_access_token`，与 settings 应用**完全撞名**（复制残留）；同 origin 部署会互相覆盖登录态与主题。
- 【坑】ehr 额外支持 `?theme=light|dark`（iframe 场景，只改 DOM 不写回偏好），`index.html` 也多读 URL 参数。
- 【坑】多数 theme store 注册 `focus` 监听但没有 `removeEventListener`；Pinia 单例通常可接受，热更新/测试重挂载会累积。

## 4. 路由守卫与 SSO 回调

- 【共性】典型守卫：渲染标题 → `meta.public` 放行 → `store.bootstrap()` 失败跳授权 → `meta.perm && !store.can(...)` 返回 `/no-access` → 放行；`/no-access` 自身标 public，页面用 `a-result status="403"`；兜底路由重定向本应用首页。
- 【坑】"base 三件套"：vite `base` / `createWebHistory(base)` / request.ts 的 callback 路径必须一致（forum-admin `/admin/`、portal `/portal/`），漏一处会出现 public base URL 校验错误或 OAuth redirect_uri 不匹配。
- 【共性】CallbackView：`code` + `state` → `consumeState()` → `exchangeCode(code, callbackUrl())` → `setTokens` → `store.bootstrap()` → `router.replace(takeRedirect())`；`expired=1` 表示 401 被踢回，必须重走授权。
- 【例外】state 校验两种口径：多数应用仅 `console.warn('state mismatch, continue without blocking')`，**forum-web / forum-admin 直接阻断**；新增回调页必须明确选一种，不要混搭。
- 【坑】防 `/callback` 自循环：多处显式过滤 redirect 不能以 `/callback` 开头（job 的 `safeRedirect()`、forum 等）。
- 【坑】并发 401 只让一次跳授权：job 用 `job_oauth_at` 时间窗 + 最近 5 个 state，否则浏览器按先发起的 state 跳走、回调必失败。
- 【坑】全局登出必须调 `/api/auth/logout` 作废旧 token（`HttpOnly` cookie 还在，只清 localStorage 会静默重登）；job 跳 `/login?gl=1` 同理。
- 【例外】job 路由**没有鉴权守卫**，登录由 `App.vue` 的 `user.ensureLogin()` 驱动；portal 只静默 bootstrap 不拦路；sso 是 IdP 无 `/callback` 而有 `/authorize`。

## 5. 布局、表格与样式类的真实定义位置

- 【共性】9 个后台应用布局骨架：`a-layout.shell` → `header`（`.logo/.menu-toggle/.right` 内挂 `AppCenterNav` + `ThemeToggleIcon` + 用户下拉）→ `body` → `a-layout-sider.sider`（宽 208，`v-if="store.can(...)"` 控制菜单项）→ `a-layout-content.content` 放 `router-view`。`.shell{height:100vh;overflow:hidden}`、`.body{min-height:0}`、`.content{padding:16px;overflow-y:auto}`。
- 【例外】job 布局在 `src/layouts/JobLayout.vue` 且菜单**不做 `store.can` 过滤**；sso 布局在 `src/views/admin/AdminLayout.vue`。
- 【共性】`ft-page/ft-head/ft-scroll/ft-bottom/ft-table` 定义在**各应用 `src/styles/global.css`**（sso/audit/cms/ehr/file/openapi/settings 七个应用），**不在 `_shared/`**：`.ft-scroll` 是唯一滚动容器，`.ft-bottom` 让分页 `position:sticky; bottom:0; z-index:5`，`.ft-table` 最后一列 `position:sticky; right:0; z-index:3`。
- 【坑】`polaris-job-web`（用 `theme.css`）和 `forum-admin-web` 的 global.css **未发现** `ft-*` 定义；新增列表页前先看本应用是否已有这套类，不要以为全局都有。
- 【共性】长文本列用 `<a-tooltip :title="完整值"><span class="cell-ellipsis">…</span></a-tooltip>` 实现「列宽内省略显示、悬浮浮层展示全文」。`cell-ellipsis` 权威定义在 `_shared/styles/responsive.css:31-38`（`display:block; max-width:100%; overflow:hidden; text-overflow:ellipsis; white-space:nowrap`），注释明确"完整内容统一交给 a-tooltip"。
- 【共性】抽样核对：**所有用 `cell-ellipsis` 的地方都配了 `a-tooltip`**。标准形态 `<a-tooltip :title="record.xxx"><span class="cell-ellipsis">{{ record.xxx }}</span></a-tooltip>`，空值用 `v-if` + `v-else` 显示 `-`；承载标签有 span/div/a/b variants。
- 【坑】flex 行内必须在 `.cell-ellipsis` 上补 `flex:1 1 auto; min-width:0` 省略号才生效（job `JobListView.vue:768`、file `FilesView.vue:473`、sso `ApplicationListView.vue:442` 等各自补了，全局规则里没有）。
- 【坑】原生 `title` 属性代替 `a-tooltip` 的例外：cms `DashboardView.vue:127/139`、sso `DashboardView.vue:255/262`、ehr `RecruitView.vue:89/93/99/204/285`、openapi `DashboardView.vue:157` 等，且多无 `cell-ellipsis`——不要照抄，统一用 `a-tooltip`。
- 【坑】`EventsView.vue:87` 显示 `record.action` 却让 tooltip 展示 `record.requestPath`（字段错位），`slice(0,60)` 硬截断见 forum `DraftsView.vue:15`——新增时 title 必须绑定被省略的同一字段，不硬截断。
- 【坑】`.tbl-empty` 全仓**没有 CSS 定义**（只在 5 个 Vue 的 `<style scoped>` 中重复定义），`.org-empty` 未发现定义；不要照 README 泛称当作已有全局类。`.drawer-body-flex` 只在 file / openapi 两个应用的 global.css 定义、实际只 openapi `DashboardView.vue` 3 处使用。
- 【坑】操作列固定：主流是 `.ft-table` CSS sticky right（global.css 注释"不用 antd 的 fixed:'right'，那会在表格内部再开一层横向滚动"），sso `ApprovalListView.vue:358` 有明确注释；antd `fixed:'right'` 全仓仅 audit `EventsView.vue:165` 一处，是历史例外，新增不要用。
- 【共性】6 个应用（cms/ehr/file/forum-admin/openapi/settings）有 `src/composables/useSessionGuard.ts`：20s 心跳 `GET /api/auth/me` + `visibilitychange` 立即探测，`onBeforeUnmount` 清 interval 与监听；sso/audit/job/portal/forum-web 没有。

## 6. 通用工具与命名

- 【共性】10 个应用有 `src/utils/appTitle.ts`：`cache/pending` 两个 Map 去重并发，`resolveAppName(appCode)` 拉正式名，`renderAppTitle(pageTitle)` 设 `document.title = '页面 · 北极星X'`，失败保留内置兜底名；forum 系用 `applyAppTitle()` 只显示应用名。
- 【共性】token 工具键名表：sso `sso_*`、audit `audit_*`、cms `cms_*`、ehr `ehr_*`、file `file_*`、forum-admin `forum_admin_*`、portal `portal_*`（无 refresh）、settings `settings_*`、forum-web `forum_*`（在 `auth.ts`）；job 另有 `job_account_*` 且导出 `get/setAccountId/Name/Super/Perms`（super 存 `'1'/'0'`）。**openapi 撞用 `settings_*`（坑）**。
- 【共性】`ThemeToggleIcon.vue` 8 个后台应用一致：`{mode, size?}`、`size` 默认 16；forum-admin 是简化版，forum-web/portal 没有。
- 【坑】`job-web/src/components/ThemeToggleIcon.vue` 与 `settings-web/src/views/SecurityView.vue` 带 UTF-8 BOM，编辑时不要破坏。
- 【共性】`confirmAction(opts)` 在 cms/forum-admin/sso 的 `src/utils/confirm.ts`：`Modal.confirm({centered:true, onOk})`，`onOk` 返回 Promise 时自动 loading。
- 【例外】抽屉宽度**没有统一标准**（实测 480/520/560/720/760/860/1000/1200/72%），按内容选，不要假设有固定档位；`fmtTime(s => s.replace('T',' ').slice(0,19))` 在多处内联重复，仅 sso 有共享 `utils/time.ts`。

## 7. 工程配置与构建

- 【共性】11 个应用 `vite.config.ts`：`@ → ./src`、`@shared → ../_shared` 别名，`server.proxy['/api'] → VITE_SSO_TARGET || http://127.0.0.1:9080`，除 forum-web 外 10 个还有 `/actuator` 代理。
- 【共性】`tsconfig.json`（8 个应用）：`strict`、`moduleResolution:'bundler'`、`noEmit`、`paths` 含 `@/*` 与 `@shared/*`、`include` 含 `../_shared/**/*.{vue,ts}`。
- 【坑】portal 与 forum-web 的 `include` **只含** `../_shared/**/*.vue`，不含 `.ts`；在 `_shared` 新增 TS 文件并被这两应用引用会类型解析失败。job 关掉 `noUnusedLocals/noUnusedParameters`。
- 【共性】env：`VITE_SSO_TARGET`（配置期 `process.env`）、`VITE_SSO_ORIGIN`（运行期，回退 `DEV ? localhost:5173 : https://polaris-sso.laimengchao.site`）；例外 `VITE_PORTAL_ORIGIN`/`VITE_FORUM_ORIGIN`/`VITE_FORUM_ADMIN_ORIGIN`/`VITE_FORUM_CLIENT_ID`。
- 【坑】`localhost` 与 `127.0.0.1` 是不同 origin，不要混用；仓库内未发现任何 `.env` 文件。
- 【坑】`polaris-web` 下**所有源文件和配置文件均为 CRLF**（含 `_shared`），新增文件用 LF 会产生整文件 diff；部分后端文件另有混合换行，提交前用 `git diff --ignore-space-at-eol` 核对。
- 【坑】`build` 脚本统一 `vue-tsc --noEmit && vite build`，不要删类型检查；沙箱清理守卫拦截时用 `--outDir /tmp/<唯一目录> --emptyOutDir false`；各应用根目录有大量 `dist_old_*` 备份目录，构建/部署脚本易误打包。
- 【坑】监听器必须成对清理：`useResponsiveCollapsed` / `AppCenterNav` / 6 个 `useSessionGuard` / forum 的 unauthorized 事件 / job 与 portal 的 resize 都在 `onBeforeUnmount` 移除。

## 8. 新增前端页面补充自查

- [ ] 先确认本应用的解包口径（A/B/C）、401 行为、token 键名、是否有 `ft-*` 与 `useSessionGuard`，再复制别的应用代码。
- [ ] 后台布局按顺序引入 `@shared/styles/responsive.css` 并使用 `useResponsiveCollapsed()`；`base` 三件套成对。
- [ ] 新增 OAuth 逻辑保留 state、callback 完整地址与 `expired=1` 分支，明确 state 校验口径，避免 `/callback` 自循环。
- [ ] 颜色走 CSS 变量，两套主题验证；长文本 `cell-ellipsis` + `a-tooltip`，不写 JS 硬截断。
- [ ] 新文件换行符与周边一致（CRLF），不破坏 BOM 文件；监听器成对清理。
- [ ] 改 `_shared/` 时 11 个应用全部跑 `vue-tsc --noEmit` + `vite build`。
