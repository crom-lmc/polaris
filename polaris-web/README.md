# Polaris Web（前端）

Polaris 一体化企业平台前端，包含统一账号中心、EHR、配置与审批中心、CMS、门户、调度中心、论坛和**接口开放平台管理端**。各应用独立开发、独立构建、独立部署，通过统一 SSO 登录，并经 Gateway 访问后端。

技术栈：Vue 3 + TypeScript + Vite 5 + Pinia 2 + Ant Design Vue 4 + axios + vue-router。

> 本仓库是前后端一体化仓库：`polaris/` 下包含 `polaris-server/`（后端，见 [后端 README](../polaris-server/README.md)）与 `polaris-web/`（前端，本文）。只需 clone 一次即可拿到全部代码。

---

## 一、从 clone 到启动：本地部署

### 1.1 准备环境

```bash
node -v   # 推荐 Node.js 20 LTS
npm -v
```

同时需要后端已就绪：至少启动 Gateway(9080)、Auth(9001)、Account(9002)、MySQL、Redis；按使用范围再启动 Settings(9007)、EHR(9006)、CMS(9004)、Portal(9005)、Job(9008)、Forum(9009)、OpenAPI(9010)。详见 [后端 README](../polaris-server/README.md)。

### 1.2 获取代码

```bash
# 一体化仓库：前后端一次 clone
git clone https://github.com/crom-lmc/polaris.git
cd polaris/polaris-web
```

### 1.3 启动应用

每个应用一个终端：

```bash
cd polaris-sso-web
npm ci            # 首次拉取或 CI 用；更新依赖用 npm install
npm run dev       # http://localhost:5173
```

其他应用同理：

```bash
cd polaris-ehr-web        # http://localhost:5179
cd polaris-settings-web   # http://localhost:5180
cd polaris-cms-web        # http://localhost:5177
cd polaris-portal-web     # http://localhost:5178
cd polaris-job-web        # http://localhost:5181
cd polaris-forum-web      # http://localhost:5182
cd polaris-forum-admin-web # http://localhost:5183，访问需带 /admin/
cd polaris-openapi-web    # http://localhost:5184
```

普通应用的 Vite 开发服务器把 `/api`、`/actuator` 代理到 `VITE_SSO_TARGET`（默认 `http://127.0.0.1:9080`）。未登录时会跳转账号中心，登录后通过一次性 ticket 返回。

> 论坛两个前端和开放平台前端需注入对应应用密钥，否则单点换票会报「未配置应用密钥」：论坛 `FORUM_APP_SECRET` / `FORUM_ADMIN_APP_SECRET`，开放平台 `SSO_OPENAPI_APP_SECRET`。这些值必须与账号中心应用登记一致，且只在服务端/后端环境变量配置，不要写进前端产物。

---

## 二、从 clone 到上线：生产部署

生产环境约定：前端源码在 `/opt/polaris/src/polaris/polaris-web/`，构建产物发布到 `/opt/polaris/www/<站点>/`，Nginx 负责域名与 HTTPS。

### 2.1 首次上线准备

```bash
sudo mkdir -p /opt/polaris/src && sudo chown -R $USER /opt/polaris
git clone https://github.com/crom-lmc/polaris.git /opt/polaris/src/polaris
cd /opt/polaris/src/polaris/polaris-web
```

1. 安装 Node.js 20 LTS。
2. 在账号中心为每个应用登记 `base_url`（正式 HTTPS origin），并配置对应 appSecret（后端环境变量）。
3. 准备 Nginx 站点模板与证书，开启 HTTPS。

### 2.2 构建与发布单个应用

以接口开放平台管理端为例：

```bash
cd /opt/polaris/src/polaris/polaris-web/polaris-openapi-web
npm ci
npm run build                       # 内含 vue-tsc --noEmit && vite build
sudo mkdir -p /opt/polaris/www/openapi
sudo cp -r dist/. /opt/polaris/www/openapi/
```

其余应用同理，站点目录建议：`sso`、`ehr`、`settings`、`cms`、`portal`、`job`、`forum`、`admin`、`openapi`。构建产物用新 dist 覆盖旧 dist 前，建议先备份：

```bash
sudo cp -a /opt/polaris/www/openapi /tmp/openapi-dist-bak-$(date +%Y%m%d_%H%M%S)
```

### 2.3 批量构建（PowerShell）

```powershell
$apps = @(
  "polaris-sso-web", "polaris-cms-web", "polaris-portal-web", "polaris-ehr-web",
  "polaris-settings-web", "polaris-job-web", "polaris-forum-web",
  "polaris-forum-admin-web", "polaris-openapi-web"
)
foreach ($app in $apps) { Push-Location $app; npm ci; npm run build; Pop-Location }
```

> 仓库内 `../polaris-server/scripts/server-deploy.sh` 提供一键构建发布，但脚本里的源码路径仍是旧的 `/opt/polaris/src/{polaris-server,polaris-web}` 双目录布局。使用一体化仓库时，请先把脚本中的 `SRC/polaris-server`、`SRC/polaris-web` 改为 `SRC/polaris/polaris-server`、`SRC/polaris/polaris-web`。

### 2.4 Nginx 配置要点

使用 vue-router history 模式时必须有 history fallback，否则刷新子路由 404：

```nginx
server {
    listen 443 ssl;
    server_name openapi.example.com;
    root /opt/polaris/www/openapi;
    index index.html;

    ssl_certificate     /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location / { try_files $uri $uri/ /index.html; }

    location /api/ {
        proxy_pass http://127.0.0.1:9080;   # 只转发到 Gateway
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 2.5 发版验证清单

- `nginx -t && nginx -s reload` 通过；首页与静态资源 HTTP 200。
- 直接打开子路由、刷新页面不 404。
- SSO 跳转、ticket 换取、退出登录正常。
- 按钮权限与数据范围与账号授权一致。
- 文件上传下载、深浅色主题跨应用一致。
- 开放平台：概览/日志方向文案（出站=数据流向平台外，入站=数据流向平台内）、调用方向箭头、第三方换票与接口调用正常。

生产必须全站 HTTPS，避免 Mixed Content；前端产物与环境变量不得包含任何服务端密钥。

---

## 三、应用与端口总览

| 应用目录 | 入口 | 开发端口 | 主要使用者 |
|---|---|---:|---|
| `polaris-sso-web` | 统一账号中心 | 5173 | 全员、管理员 |
| `polaris-cms-web` | 内容中心 | 5177 | 内容运营 |
| `polaris-portal-web` | 内容门户 | 5178 | 员工、访客 |
| `polaris-ehr-web` | 人力中心 | 5179 | 全员、HR、负责人 |
| `polaris-settings-web` | 配置与审批中心 | 5180 | 管理员、审批人 |
| `polaris-job-web` | 调度中心 | 5181 | 运维 |
| `polaris-forum-web` | 论坛前台 | 5182 | 全员 |
| `polaris-forum-admin-web` | 论坛管理端 | 5183 | 论坛运营（base `/admin/`） |
| `polaris-openapi-web` | 接口开放平台管理端 | 5184 | 开放平台管理员 |

---

## 四、功能模块

### 4.1 统一账号中心（`polaris-sso-web`）

面向全员与管理员。登录页 `LoginView`、授权页 `AuthorizeView`；登录后的管理后台 `/admin/`（由 `AdminLayout` 承载）包含：

- **工作台与个人信息**：`DashboardView` 概览、`ProfileView` 个人资料、`PasswordView` 修改密码。
- **主数据与权限**：`AccountListView` 账号、`RoleListView` 角色、`PermissionListView` 权限码、`ApplicationListView` 应用登记与授权、`MyApplicationsView` 我的应用。
- **审批与通知**：`ApprovalListView` 审批、`NoticeCenterView` 公告、`NotificationCenterView` 通知。
- **审计**：`LoginLogView` 登录日志。

前端只按权限码控制入口与按钮，数据范围由服务端执行。

### 4.2 人力中心（`polaris-ehr-web`）

15 个页面，覆盖员工全生命周期：

- **首页与组织**：`DashboardView` 指标看板、`OrgView` 组织树、`EmployeeView` 人员管理与档案抽屉。
- **假勤**：`AttendanceView` 考勤、`LeaveView` 请假、`AnnouncementView` 公告。
- **员工自助**：`MyRegularizeView` 我的转正、`MyResignView` 我的离职、`CallbackView` 审批回调落地页。
- **HR 管理**：`RecruitView` 招聘、`ApprovalView` 审批、`EmpChangeApprovalView` 异动审批、`HrLedgerView` 人力台账、`AuditLogView` 审计日志。
- `NoAccessView` 无权限提示。台账支持服务端筛选/分页/图表；数据权限在服务端执行，前端隐藏按钮不替代鉴权。

### 4.3 配置与审批中心（`polaris-settings-web`）

- **基础数据**：`DictView` 字典类型与字典项、`CodeRuleView` 单号规则、`BizDataView` 主数据、`BizPointView`/`EmbedPointView` 业务点位与嵌入点。
- **审批**：`ApprovalSceneView` 审批场景/模板/流程配置、`ApprovalView` 统一待办已办我发起、`CallbackView` 回调落地页。
- **消息与安全**：`NoticeConfigView` 通知配置、`SecurityView` 安全配置（初始密码、复杂度、失败锁定、会话）。
- `DashboardView` 概览、`NoAccessView` 无权限提示。

### 4.4 CMS / Portal

**CMS（`polaris-cms-web`）**：`ArticleListView` + `ArticleEditView`（Markdown 编辑、草稿/提交审批/发布/下架）、`CategoryView` 树形栏目、`TagView` 标签、`BannerView` Banner 与推荐位、`PortalLayoutView` 门户布局、`MediaView` 媒体库、`FriendLinkView` 友链、`DashboardView` 概览。

**Portal（`polaris-portal-web`）**：`HomeView` 门户首页、`ArticleListView`/`ArticleView` 文章列表与详情、`CareersView` 招聘展示、`JobDetailView`/`JobProgressView` 职位详情与进度、`CallbackView` 回调落地页。聚合 CMS 公开内容、settings 待办与 EHR 招聘，支持深浅色主题。

### 4.5 调度中心（`polaris-job-web`）

按目录组织：`jobs` 任务定义与 Cron、启停/手工触发；`executors` 执行器注册与心跳；`logs` 执行日志与失败原因；`alarms` 失败报警规则；`dashboard` 概览；`callback` 回调落地页。

### 4.6 论坛

**前台（`polaris-forum-web`，18 个页面）**：`HomeView` 首页、`BoardView` 板块、`ContentFeedView` 内容流、`ThreadView`/`NewThreadView` 帖子浏览与发布、`DraftsView` 草稿、`FavoritesView` 收藏、`NotificationsView` 通知；`PointsView` 积分、`LevelsView` 等级、`BadgesView` 徽章、`GiftsView` 礼品兑换；`LotteryView` 抽奖（方形九宫格/转盘双形态）；`MemberView`/`ProfileView`/`ProfileEditView` 会员主页与资料；`AboutView` 关于。

**管理端（`polaris-forum-admin-web`，base `/admin/`）**：`OverviewView` 概览、`TaxonomyView` 一级板块/二级栏目/标签、`ThreadsView`/`CommentsView` 内容审核、`GiftsView`/`ExchangesView` 礼品与发放记录（含来源列）、`LotteryView` 抽奖配置与奖项权重、`BadgesView`/`LevelsView` 徽章等级、`SensitiveWordsView` 敏感词、`ReportsView` 举报、`SiteView` 站点设置。使用独立权限码（如 `forum:lottery`），归「论坛活动运营」角色。

### 4.7 接口开放平台管理端

`polaris-openapi-web`（端口 5184，生产域名 `polaris-openapi.laimengchao.site`）是开放平台的管理控制台，8 个功能页 + 回调落地页，全部通过 SSO 用户登录 + 权限码访问 `/api/open/admin/**`：

- **概览 `DashboardView`**：状态指标卡片、调用趋势、失败 TOP、最近异常；表格/详情统一用抽屉展示；方向按数据流向标注（出站=对外提供、入站=调用第三方）。
- **API 管理 `EndpointView`**：注册/维护开放接口，配置方向、操作类型、权限码、限流、幂等、状态；启用状态的接口不能删除，只能停用。
- **对接应用 `AppView`**：维护第三方应用（appCode/名称/状态/IP 白名单）；新增/查看密钥/重置密钥（密钥抽屉区分查看/重置/新建三种模式）；有接口授权的应用「删除」按钮置灰并提示，后端同时兜底拦截。
- **应用授权 `AuthzView`**：按接口粒度勾选授权（唯一键 `(app_code, api_code)`），支持停用/启用；停用/删除单个接口不联动其他接口；顶部可按应用筛选。
- **出站渠道 `ChannelView`**：配置平台调用外部的渠道（目前邮件 SMTP）的鉴权方式、超时、重试、允许应用与凭证托管；凭证只回显「是否已配置」，不明文展示。
- **调用日志 `LogView`**：Trace ID、方向、**调用方向**（箭头直观展示「应用→平台」或「平台→渠道」）、应用/API/渠道、HTTP 状态、耗时、时间；详情抽屉含三张卡片；支持按条件筛选与导出。
- **统计报表 `StatView`**：按方向/应用/时间统计调用量、成功率、耗时分布、TOP 应用与接口。
- **平台设置 `SettingView`**：令牌有效期、签名容差、限流等全局参数。

页面统一遵循：弹窗改抽屉、分页固定右下角、空状态铺满、操作列左对齐不换行、确认框居中；详情见 `src/styles/global.css` 的 `.drawer-body-flex`。

---

## 五、技术栈与通用结构

| 分类 | 技术 |
|---|---|
| 框架 | Vue 3、`<script setup lang="ts">`、TypeScript 5.6 |
| 构建 | Vite 5、`vue-tsc --noEmit` |
| UI | Ant Design Vue 4.2.6 |
| 状态与路由 | Pinia 2、vue-router |
| 时间/编辑器 | dayjs、md-editor-v3（CMS 等按需） |
| 包管理 | npm |

典型应用目录：

```text
polaris-xxx-web/
├── src/
│   ├── api/          # 业务接口与 request 封装
│   ├── components/   # 组件、抽屉、审批组件
│   ├── router/       # 路由、守卫、权限过滤
│   ├── stores/       # 用户、权限码、主题
│   ├── styles/       # CSS 变量与深浅色主题
│   ├── views/        # 页面
│   └── main.ts
├── index.html        # 首屏主题初始化
├── vite.config.ts    # 开发端口与后端代理
└── package.json
```

请求链路：页面 → `src/api/*.ts` → request 注入登录态 → Vite `/api` 代理 → Gateway:9080 → 业务服务。登录后从 store 取用户与应用权限，路由守卫判断登录态，菜单/按钮用 `store.can('资源:动作')` 控制。

主题分 `light`/`dark`，通过 Ant Design Vue 算法与 CSS 变量实现；共享 cookie `polaris_theme` 同步同域应用，读取优先级 cookie > 本地 localStorage > 系统偏好；`index.html` 在渲染前设置 `data-theme` 减少闪烁。

---

## 六、环境变量与 SSO 联调

| 变量 | 默认值/示例 | 用途 |
|---|---|---|
| `VITE_SSO_TARGET` | `http://127.0.0.1:9080` | Vite 开发代理目标（Gateway） |
| `VITE_SSO_ORIGIN` | `http://localhost:5173` | 账号中心地址，用于跳转登录 |

```powershell
$env:VITE_SSO_TARGET = "http://127.0.0.1:9080"
$env:VITE_SSO_ORIGIN = "http://localhost:5173"
npm run dev
```

变量在 Vite 配置阶段读取，修改后需重启；前端变量会进入浏览器产物，**不要放数据库密码、appSecret、JWT 私钥或内部 token**。

本地推荐地址（不要混用 `localhost` 与 `127.0.0.1`，它们是不同 origin）：

```text
账号中心 5173 / CMS 5177 / Portal 5178 / EHR 5179 / Settings 5180
Job 5181 / 论坛前台 5182 / 论坛管理 5183 / 开放平台 5184 / Gateway 9080
```

应用登记检查项：应用已启用且当前账号有权限；`base_url` 与浏览器 origin 完全一致；后端 `*_APP_SECRET` 与登记值一致；账号具备角色、功能权限与数据范围。

推荐联调顺序：先 Account/Auth/Gateway + sso-web 确认登录；再 Settings/EHR + ehr-web 确认回跳与获取当前用户；给测试账号较小权限验证菜单/按钮/数据；最后按需启动其他应用。

---

## 七、类型检查、构建与预览

```bash
npm run build     # = vue-tsc --noEmit && vite build，产物在 dist/
npm run preview   # 本地检查构建结果，非生产服务器
npx vue-tsc --noEmit   # 只做类型检查
```

---

## 八、开发规范

- 组件用 `<script setup lang="ts">`；接口集中在 `src/api/*.ts`，组件不散落写请求；类型与后端 DTO 对齐。
- 权限通过 store 获取，按钮/入口用 `store.can('资源:动作')`；新增菜单同时评估路由、页签、按钮、后端 `@Permit` 与数据范围。
- 颜色用 CSS 变量并同时适配浅深；表格布局沿用 `ft-page / ft-head / ft-scroll / ft-bottom`，空态用 `.tbl-empty`，树/面板空态用 `.org-empty`；主题读写走 `src/stores/theme.ts`。
- `.env.local`、`dist/`、`node_modules/` 不提交；提交前执行 `npm run build`。

---

## 九、常见问题

1. **`npm run dev` 后接口连接失败**：确认 Gateway 在 9080、`VITE_SSO_TARGET` 指向它、所需后端服务已启动；Vite 只代理请求，不启动后端。
2. **登录后跳回登录页 / ticket 失败**：检查 `base_url`、浏览器 origin、应用授权与后端 `*_APP_SECRET`；改端口后同步应用登记。
3. **请求 401**：检查登录过期、RSA 密钥配对、Redis、是否经过 Gateway。
4. **有菜单无按钮**：按钮有独立权限码，重新登录后刷新权限缓存；非 HR 对组织/人员只读是预期行为。
5. **有页面无数据**：组织数据范围在服务端生效，不要通过请求全部接口绕过。
6. **跨域错误**：本地用 Vite `/api` 代理，生产用 Nginx 转发 `/api` 到 Gateway。
7. **环境变量不生效**：`VITE_*` 在 Vite 启动时读取，重启开发服务器；确认变量设在执行 `npm run dev` 的终端。
8. **生产首页正常、刷新子路由 404**：Nginx 缺 `try_files $uri $uri/ /index.html;` 或 `root` 指错目录。
9. **构建报 TypeScript 错误**：先修复类型错误，不要删除类型检查脚本。
