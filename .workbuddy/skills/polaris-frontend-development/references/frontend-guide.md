# Polaris 前端开发规范

> 适用范围：`polaris-web/` 下 11 个独立 Vue 应用与 `_shared/` 共享目录。
> 本文是基于现有代码事实总结的**强制约定**。开始前请先读 `../../../rules/frontend-00-L1-redlines.md` 与 `frontend-01-L2-conventions.md` 的硬约束。
> 发现本文与代码冲突时，以代码为准并回头修正本文。

---

## 1. 技术栈（版本已锁定）

| 项 | 版本 / 选型 |
|---|---|
| Vue | 3.5，全部 `<script setup lang="ts">`（job 的部分 store 用 Options API） |
| Vite | 5.4.x；**例外**：`polaris-forum-web` 为 6.4.x |
| TypeScript | 5.6；`vue-tsc` 2.1.x；**例外**：forum-web 为 3.3.x |
| 状态管理 | Pinia |
| UI 组件库 | Ant Design Vue（论坛前台不引入） |
| 图标 | `@ant-design/icons-vue` + 少量内联 SVG |
| HTTP | axios（10 个应用）；**例外**：forum-web 的 `api/request.ts` 用原生 `fetch` 薄封装 |
| 构建脚本 | 统一 `dev` / `build` / `preview`，其中 `build = vue-tsc --noEmit && vite build` |

**禁止**：引入新的 UI 库、状态管理库、CSS 框架、图标库。通用能力放进 `_shared/`。

---

## 2. 应用清单与部署路径

| 应用目录 | 端口 | 正式名称 | Vite base / 路由前缀 | 生产路径 |
|---|---:|---|---|---|
| `polaris-sso-web` | 5173 | 北极星钥 | `/` | 域名根 |
| `polaris-cms-web` | 5177 | 北极星源 | `/` | 域名根 |
| `polaris-portal-web` | 5178 | 北极星门 | `/portal/` | 并入 CMS `/portal/` |
| `polaris-ehr-web` | 5179 | 北极星宿 | `/` | 域名根 |
| `polaris-settings-web` | 5180 | 北极星典 | `/` | 域名根 |
| `polaris-job-web` | 5181 | 北极星轨 | `/` | 域名根 |
| `polaris-forum-web` | 5182 | 北极星空 | `/` | 域名根 |
| `polaris-forum-admin-web` | 5183 | 北极星核 | `/admin/` | 并入论坛 `/admin/` |
| `polaris-openapi-web` | 5184 | 北极星桥 | `/` | 域名根（部署口径待确认，见 §10） |
| `polaris-file-web` | 5185 | 北极星云 | `/` | 域名根 |
| `polaris-audit-web` | 5186 | 北极星眼 | `/` | 域名根 |

> 前端 README 只列了 9 个应用（缺 audit、file），以本节的 11 个为准。

应用名运行时从 `sso_application.name` 动态取（各应用 `src/utils/appTitle.ts`）。**登录接口的返回结构在 sso/audit 与其他应用之间不一致**，按本应用现有写法复制，不要跨应用套用（见 §4.2）。

---

## 3. 工程结构与命名

```text
polaris-web/
├── _shared/                       # 跨 11 应用共享，不独立构建（仅 3 个文件）
│   ├── AppCenterNav.vue
│   ├── useResponsiveCollapsed.ts
│   └── styles/responsive.css
└── polaris-<app>-web/
    ├── vite.config.ts             # @ → ./src，@shared → ../_shared
    ├── tsconfig.json              # paths + include 覆盖 _shared
    └── src/
        ├── api/*.ts               # 所有请求集中于此
        ├── views/                 # 页面级组件：XxxView.vue
        ├── components/            # 可复用业务组件
        ├── layout/ 或 layouts/    # 后台布局（job 用 layouts/，sso 放 views/admin/）
        ├── router/                # 路由定义
        ├── stores/                # Pinia：user / theme
        ├── styles/                # 全局 CSS 与主题变量
        ├── utils/                 # appTitle / token / 格式化
        └── main.ts                # 入口
```

**目录差异按实际走，不要强行统一**：

- 多数应用用 `src/layout/`，只有 `polaris-job-web` 用 `src/layouts/`，`polaris-sso-web` 把布局放在 `src/views/admin/AdminLayout.vue`。
- 前台 `polaris-forum-web` 没有 `stores/`，用 `src/auth.ts` / `src/theme.ts` / `src/types.ts` / `src/styles.css`。
- `composables/` 只存在于 cms / ehr / file / forum-admin / forum-web / job / openapi / settings。
- `types/` 只存在于 sso / audit / ehr。

命名：页面 `XxxView.vue`，布局 `XxxLayout.vue`，API 函数 `fetchXxx` / `saveXxx` / `deleteXxx` / `exportXxx`，类型与后端 VO 对齐；目录 kebab-case，组件文件 PascalCase。

### 3.1 共享目录引用（三位一体）

11 个应用全部配置别名与 paths：

```ts
// vite.config.ts
resolve: {
  alias: {
    '@': fileURLToPath(new URL('./src', import.meta.url)),
    '@shared': fileURLToPath(new URL('../_shared', import.meta.url))
  }
}
```

```jsonc
// tsconfig.json
"paths": { "@shared/*": ["../_shared/*"] },
"include": ["src/**/*.ts", "src/**/*.vue", "../_shared/**/*.vue", "../_shared/**/*.ts"]
```

例外：`forum-web` / `portal-web` 的 `include` 只含 `../_shared/**/*.vue`，不含 `.ts`——这两个应用不引 `useResponsiveCollapsed`，新增共享 TS 文件时要注意它们的类型可见性。

**共享模块不得依赖 `vue-router` 等应用级模块**（历史踩坑：composable 引 router 导致解析失败，已移除）。

---

## 4. API 层

### 4.1 请求封装的共同点

- `baseURL: ''`，所有请求走同源 `/api`，由 Vite / Nginx 代理到 Gateway `9080`。
- `withCredentials: true` 必须保留：跨应用单点依赖 HttpOnly 的 `SSO_SID` cookie。
- 请求拦截器注入 `Authorization: Bearer <token>`；登录/换票接口不带旧令牌。
- 业务码 `code !== 0` 转 reject，`err.code` 记**业务码**而不是 HTTP 状态码。

### 4.2 两套响应解包口径（跨应用复制必错）

后端统一外壳是 `{ code, message, data }`，但各应用拦截器脱壳方式不同：

| 口径 | 应用 | 拦截器行为 | 调用方写法 |
|---|---|---|---|
| **A** | settings / cms / ehr / file / job / openapi / portal / forum-admin | `return body.data` | `request.get<never, T>()`，直接拿数据 |
| **B** | **sso / audit** | `return { ...response, data: body.data }` | `request.get<never, { data: T }>()`，必须 `.data` |
| C（自定义） | forum-web（fetch） | `return body.data` | 直接拿数据 |

```ts
// 口径 A（settings 系）
export function fetchMe() {
  return request.get<never, MeInfo>('/api/settings/me')
}
const info = await fetchMe()          // 直接是 MeInfo

// 口径 B（sso / audit）
export function fetchAuditEvents(params: {...}) {
  return request.get<never, { data: PageResult<AuditEvent> }>('/api/audit/admin/events', { params })
}
const res = await me(); user.value = res.data
```

新增 API 前**先看本应用现有 `src/api/*.ts` 的泛型写法**。

### 4.3 401 行为差异

| 应用 | 401 处理 |
|---|---|
| sso / audit | 有 `skipAuthRedirect` 探测开关（`/api/auth/session` 的 401 是正常分支），其余清 token 跳授权 |
| 其他后台应用 | 清 token，`window.location.replace('/callback?redirect=...&expired=1')` |
| forum-admin | 必须跳 `/admin/callback`，否则 Vite base 校验失败 |
| portal | **不跳登录**，只 reject |
| forum-web | 派发 `forum:unauthorized` 事件后跳 SSO |

超时：sso / audit 为 20s，其他业务应用为 30s。`job-web` 额外用 `transformResponse` + `quoteLongIntegers()` 给 16 位以上整数字面量加引号，防雪花 ID 精度丢失——新代码不要绕过这个转换。

### 4.4 blob 下载

```ts
// 口径 B 应用
export function exportAuditEvents(params: ExportParams) {
  return request.get<never, { data: Blob }>('/api/audit/admin/events/export', {
    params, responseType: 'blob'
  })
}
```

前端拿到 Blob 后触发保存；文件名从 `Content-Disposition` 或约定字段取，中文名用 `encodeURIComponent`。

---

## 5. 路由、权限与应用中心

### 5.1 路由

```ts
const router = createRouter({
  history: createWebHistory(),       // forum-admin: '/admin/'；portal: '/portal/'
  routes: [
    { path: '/callback', component: () => import('@/views/CallbackView.vue'), meta: { public: true } },
    { path: '/', component: () => import('@/layout/XxxLayout.vue'), children: [
      { path: 'dicts', component: () => import('@/views/DictView.vue'), meta: { title: '字典配置', perm: 'dict:view' } }
    ]},
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  if (!(await store.bootstrap())) { gotoAuthorize(to.fullPath); return false }
  if (to.meta.perm && !store.can(to.meta.perm as string)) return '/no-access'
  return true
})
```

- 带 base 的应用，`vite.config.ts` 的 `base`、`createWebHistory(base)`、`callback` **三者必须成对**，漏一个会报 “public base URL of /admin/” 或 OAuth `redirect_uri` 与 `sso_application.redirect_uri` 不匹配。
- `meta.perms: string[]` 只有 EHR 使用；其余用 `meta.perm`。
- 审计的登录审计子路由**不能叫 `login`**（会与登录页冲突），现用 `login-audit`。
- SSO 的审批中心 / 消息中心刻意不挂 `meta.perm`，加权限码会挡住普通员工。

### 5.2 菜单与按钮权限

**没有 `v-perm` 指令**（全仓无实现，不要凭旧文档写）。实际只有三种方式：

1. Pinia user store 的 `store.can('资源:动作')`：`superAdmin || permissions.includes(code)`。
2. job 用 Options API getter `hasPerm`，模板写 `v-if="user.hasPerm('job:manage')"`。
3. 路由 `meta.perm` + `beforeEach` 统一拦截。

菜单目前是布局组件**硬编码** `a-menu-item` + `v-if="store.can(...)"`，不是后端下发；新增菜单要同时改布局、路由、后端权限码与 SQL 授权。

新增按钮/卡片入口同样要 `v-if="store.can(...)"` 并授权；管理员有写权限，查看员只读。

### 5.3 应用中心与回调路径

`_shared/AppCenterNav.vue` 被全部 11 个应用引用：后台放在布局顶部，门户/论坛前台**仅登录后渲染**（`v-if="store.isLoggedIn"`）。

回调在 `AppCenterNav.vue` 的 `ALL_APPS` 里写死为**完整 `redirect_uri`**（如 `https://polaris-audit.laimengchao.site/callback`），**必须与 account 中注册的 `sso_application.redirect_uri` 完全一致，不能改成首页**（首页回跳拿不到 `code`，表现为点了没登录）。路径部分规律：

| 应用 | 回调路径部分 |
|---|---|
| settings / cms / ehr / job / openapi / file / audit | `/callback` |
| forum-admin | `/admin/callback` |
| portal | `/portal/callback`（注意域名是 `polaris-cms.laimengchao.site`，不是独立 portal 域名） |
| forum-web | `/auth/callback`（在 forum-web 自身的 `auth.ts`，不在 ALL_APPS） |
| sso 自身 | `callback: ''`，签码页走 `/login`、`/authorize` |

应用中心签码走 `POST /api/auth/oauth/authorize`，参数 `responseType: 'code'` / `clientId` / `redirectUri` / `state`，`credentials: 'include'` 带中心会话。token 存储键各应用不同，共享组件用正则 `/access.?token$/i` 扫描 localStorage，不写死键名。图标走 `${SSO_ORIGIN}/icons/<app.icon>.svg`，对应 sso 应用的 `public/icons/`。

分组标题读取 `/api/settings/public/dicts/sso.application_type`，与 SSO「我的应用」同源；失败回退内置分组。

---

## 6. 组件与表格

### 6.1 列表页标准结构

Ant Design Vue 全量注册（`app.use(Antd)` + `import 'ant-design-vue/dist/reset.css'`）；论坛前台不引 Antd。

分页固定右下角的实际写法是**给滚动容器同时加两个类**：

```vue
<section class="ft-scroll ft-bottom">
  <a-table :columns="columns" :data-source="list" :pagination="false"
           :class="{ 'tbl-empty-mode': !loading && !list.length }">
    <template #bodyCell="{ column, record }"> ... </template>
    <template #emptyText>
      <div class="tbl-empty"><a-empty v-if="!loading" description="暂无数据" /></div>
    </template>
  </a-table>
</section>
```

- 分页 `position: sticky; bottom: 0; margin: auto 0 0; z-index: 5`，必须高于 sticky 操作列的 `z-index: 3`。
- 空数据铺满内容区 MUST 三件套：上例的 `.tbl-empty-mode` 触发类 + `#emptyText` 插槽 + global.css/scoped `:deep()` 中的 `.tbl-empty-mode` 表格链与 `.tbl-empty` 定义（完整 CSS 见 `new-feature-guide.md` §3.1）。只写插槽不绑类/不定义样式 → 空态只占一行、下方留白。空树/面板用 `.org-empty`。
- 抽屉宽度按内容选 420 / 480 / 520 / 640 / 760 / 820 / 900 / 960 / 1000；宽内容用 `'min(1440px, 94vw)'`。抽屉内表格用 `class="drawer-body-flex"`。
- 视觉总口径：弹窗改抽屉、分页固定右下角、空态铺满、操作列左对齐不换行、确认框居中。

### 6.2 长文本列（强制）

**所有可能很长的字段必须同时具备 CSS 省略 + 悬浮完整文本**。共享类 `.cell-ellipsis` 已在 `_shared/styles/responsive.css` 定义，优先复用：

```vue
<template v-else-if="column.key === 'failReason'">
  <a-tooltip :title="record.failReason || '-'">
    <span class="cell-ellipsis muted">{{ record.failReason || '-' }}</span>
  </a-tooltip>
</template>
```

```css
/* 仅当共享类不适用时才在页面内写 */
.cell-ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
```

- 高风险字段：失败原因、User-Agent、详情/摘要、备注、路径、消息、标题/名称、参数、堆栈。
- **禁止用 `slice(0, 36)` 之类 JS 硬截断**代替省略号——信息会永久丢失。确需手工截断时外层必须有 `a-tooltip :title="完整值"`。
- 多行用 `line-clamp` 时也要配 `title` 或 tooltip。

---

## 7. 样式、主题与响应式

### 7.1 主题

- 颜色用 CSS 变量并适配浅深两套：`--bg-page`、`--text-base`、`--header-bg`、`--sider-bg`、`--muted`、`--border-subtle` 等，定义在各自 `src/styles/global.css` 的 `:root` 与 `:root[data-theme='dark']`。
- `--primary` **不是全局统一定义**：只有 forum-web 与 job-web 定义了它；`AppCenterNav` 用 `var(--primary, #2f54eb)` 兜底，其他应用靠 antd token `colorPrimary: '#2f54eb'`。新增共享组件用兜底写法。
- 三层机制：① `index.html` 内联脚本首屏读共享 cookie `polaris_theme` > 本应用 localStorage（`sso_theme` 等）> `prefers-color-scheme`，设置 `data-theme`；② `stores/theme.ts` 写回并监听 `focus` 同步；③ `a-config-provider` 用 `antdTheme.darkAlgorithm/defaultAlgorithm`。
- antd-vue 4.2.6 的 Layout 深色 token 实测不生效，底色必须走 CSS 变量，改完要在浅色/深色两套都验证。

### 7.2 响应式（9 个后台应用统一）

两步接入，**引入顺序必须在应用自身 `global.css` 之后**：

```ts
// src/main.ts
import 'ant-design-vue/dist/reset.css'
import './styles/global.css'
import '@shared/styles/responsive.css'
```

```vue
<!-- layouts/XxxLayout.vue -->
<script setup lang="ts">
import { useResponsiveCollapsed } from '@shared/useResponsiveCollapsed'
const collapsed = useResponsiveCollapsed()   // 默认 (max-width: 1024px)
</script>
<template>
  <menu-outlined class="menu-toggle" @click="collapsed = !collapsed" />
  <AppCenterNav current-code="xxx" />
</template>
```

- `responsive.css` 断点：1024（侧栏抽屉化 + 遮罩 + 内容区清 margin）、768（两列 / 表格横滚）、640（单列）、480（压缩）。
- `useResponsiveCollapsed` 首屏按视口初始化避免闪烁，跨断点自动切换；点击 `.sider` 内 `.ant-menu-item` 视为导航完成并收起，点 `.menu-toggle` / `.ac-panel` 不收起。
- forum-web / portal-web 是前台、无侧栏，只引 `AppCenterNav`，但卡片列表仍需窄屏降级（用现有的 ellipsis / line-clamp）。
- 新增后台应用或布局照此接入，**不要自己写一套媒体查询**。

---

## 8. 类型检查与构建

```bash
cd /f/code/polaris/polaris-web/polaris-<app>-web
node_modules/.bin/vue-tsc.cmd --noEmit
node_modules/.bin/vite.cmd build
# 清理守卫拦截时（见下）
node_modules/.bin/vite.cmd build --outDir /tmp/<唯一目录> --emptyOutDir false
```

- 沙箱的 `node-safe-delete-shim` 会拦 Vite `emptyDir`；`dist/assets` 超过 50 文件时报 `SAFE_DELETE_BULK_CONFIRM_REQUIRED`，表现为构建卡在清空 dist、产物仍旧。用仓库外唯一临时输出目录规避，不改变产物内容。
- 批量验证用唯一目录，避免应用间覆盖：

```bash
cd /f/code/polaris/polaris-web
VOUT="/tmp/polaris-verify-$(date +%s)"
for d in polaris-*-web; do
  (cd "$d" && node_modules/.bin/vue-tsc.cmd --noEmit && \
   node_modules/.bin/vite.cmd build --outDir "$VOUT/$d" --emptyOutDir false >/dev/null)
done
```

- `tsconfig` 共性：`strict: true`、`moduleResolution: "bundler"`、`noEmit: true`。差异：job 关掉 `noUnusedLocals` / `noUnusedParameters`；portal 的 `@/*` 写成 `./src/*`。
- `.env.local`、`dist/`、`node_modules/` 不提交；提交前必须通过类型检查与构建。
- 改共享组件后必须跑**全部 11 个应用**，不能只跑当前应用。

---

## 9. 部署

- 产物发布到 `/opt/polaris/www/<站点>/`（`forum-admin` 对应 `admin`，`portal` 对应 `portal`）。
- 目录属主 `www-data`，写入/清理需 `sudo -n`。Nginx 必须 history fallback：`try_files $uri $uri/ /index.html;`。
- 标准流程：本地构建 → 上传 `/tmp/polaris-deploy-<时间戳>/` → 备份到 `/opt/polaris/backups/pre-deploy-<时间戳>/` → 旧目录改名 `.old_<ts>` → `cp -a` 新产物 → `chown -R www-data:www-data` → curl 自检 → 清理旧目录。
- `server-deploy.sh` 的 `build_site` 只覆盖 sso / settings / ehr / cms / portal / job / forum / admin **8 个站点**；openapi / file / audit 需单独发布，不要以为脚本会带上。
- **改动范围决定发布范围**：单应用改动只发该应用（无需重启后端）；改 `_shared/` 任一文件要发 **11 个应用**；同时改后端还要发 jar 并重启服务。

---

## 10. 项目特有坑

| 坑 | 正确做法 |
|---|---|
| 跨应用照抄 API 层 | 先确认解包口径：settings 系 `request.get<never, T>`，sso/audit 必须 `{ data: T }` |
| 以为所有应用都用 axios | forum-web 的 `api/request.ts` 用原生 `fetch` 薄封装（仍集中在 `api/`），Vite 只代理 `/api` |
| 照搬 401 跳转 | portal 不跳登录、forum-admin 跳 `/admin/callback`、sso/audit 有 `skipAuthRedirect` |
| 按通用名字找登录接口 | sso/audit 用 `auth.ts`；settings/cms/ehr/file/openapi/forum-admin 放在业务 API 文件里；forum-web 用 `auth.ts` 的 `redirectToSsoLogin` |
| 忘了雪花 ID 精度 | job 的 `transformResponse` 不能绕过 |
| base / history / callback 不同步 | 三者成对修改，尤其 `/admin/`、`/portal/` |
| 应用中心跳首页 | 必须送注册的 callback，否则门户/论坛拿不到 `code` |
| 写死 localStorage token 键 | 各应用键名不同，共享组件用正则扫描 |
| 按旧文档写 `v-perm` 指令 | 实际只有 `store.can()` / job `hasPerm()` / `meta.perm` |
| 审计子路由命名 `login` | 会冲突，用 `login-audit` |
| 给 SSO 审批/消息中心加 `meta.perm` | 会挡住普通员工访问 |
| 长文本用 JS 硬截断 | 改 CSS 省略 + `a-tooltip` |
| 写死主题色 | 用 CSS 变量，共享组件给 `--primary` 兜底，验证两套主题 |
| `responsive.css` 引在 global.css 之前 | 必须在应用自身 CSS 之后 |
| 直接 `vite build` 被清理守卫拦 | 用 `--outDir /tmp/... --emptyOutDir false` |
| 改 `_shared` 只发一个应用 | 必须发 11 个应用 |
| 以为 `server-deploy.sh` 发全部站点 | 只有 8 个，openapi/file/audit 单独发 |
| 误删 `dist_old_*` | `.gitignore` 只忽略 `dist_bak_*/`，`dist_old_*` 会显示为未跟踪；清理时用明确路径 |
| openapi 域名 | README / 应用中心写 `polaris-openapi.laimengchao.site`，但 nginx 模板无该 server 块；发版前单独确认 |
| 提交前不检查换行符 | 部分文件混合换行，`Edit` 会整文件归一化产生假 diff；用 `git diff --ignore-space-at-eol` 核对 |

---

## 11. 前端改动完成清单

- [ ] 组件用 `<script setup lang="ts">`，请求集中在 `src/api/`，解包口径与本应用一致。
- [ ] 权限用 `store.can(...)` / `meta.perm`，与后端 `@Permit`、SQL 授权一致；未新增 `v-perm` 指令。
- [ ] 长文本列有 `cell-ellipsis` + `a-tooltip`；无 JS 硬截断。
- [ ] 颜色走 CSS 变量，浅色/深色都已验证。
- [ ] 后台布局已按顺序引入 `responsive.css` 并使用 `useResponsiveCollapsed`。
- [ ] 带 base 应用的 `base` / `createWebHistory` / callback 三者一致；应用中心保留登录后显示。
- [ ] `vue-tsc --noEmit` 与 `vite build` 通过；改共享组件时 11 应用全过。
- [ ] 明确列出需要发布的静态资源；无需后端重启时已写明。
