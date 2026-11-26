# Polaris Web（前端）

Polaris 一体化企业平台前端，包含统一账号中心、EHR、配置与审批中心、CMS、门户、调度中心、论坛、**接口开放平台管理端**、**统一文件服务**、**统一审计中心**和**北极星会（会议室管理）**共 12 个独立业务应用。各应用独立开发、独立构建、独立部署，通过统一 SSO 登录，并经 Gateway 访问后端。

技术栈：Vue 3 + TypeScript + Vite 5 + Pinia 2 + Ant Design Vue 4 + axios + vue-router。

> 本仓库是前后端一体化仓库：`polaris/` 下包含 `polaris-server/`（后端，见 [后端 README](../polaris-server/README.md)）与 `polaris-web/`（前端，本文）。只需 clone 一次即可拿到全部代码。

## 在线体验

生产环境访问地址（统一 SSO 登录，单点互通）：

| 应用 | 访问地址 |
|:---|:---|
| 统一账号中心 | https://polaris-sso.laimengchao.site/ |
| 人力中心（EHR） | https://polaris-ehr.laimengchao.site/ |
| 内容中心（CMS） | https://polaris-cms.laimengchao.site/ |
| 配置中心 | https://polaris-settings.laimengchao.site/ |
| 调度中心 | https://polaris-job.laimengchao.site/ |
| 接口开放平台 | https://polaris-openapi.laimengchao.site/ |
| 北极星空论坛（前台） | https://polaris-forum.laimengchao.site/ |
| 论坛管理后台 | https://polaris-forum.laimengchao.site/admin/ |
| 内容门户 | https://polaris-cms.laimengchao.site/portal/ |
| 统一文件服务（北极星云） | https://polaris-file.laimengchao.site/ |
| 审计中心（北极星眼） | https://polaris-audit.laimengchao.site/ |
| 北极星会（会议室管理） | https://poraris-meeting.laimengchao.site/ |

**演示账号**：`yanshi` / `yanshi`（全部菜单可查看；写操作由网关拦截，提示「演示账户不能进行操作」）。

> 演示账号默认禁止登录，需管理员在「配置中心 → 安全配置」打开「演示模式」开关后方可登录（安全配置的 `demoMode` 字段，经 settings-client 提供给 sso-account 做登录前置校验）。演示账号访问安全配置页时，默认密码字段脱敏显示。

---

## 一、从 clone 到启动：本地部署

### 1.1 准备环境

```bash
node -v   # 推荐 Node.js 20 LTS
npm -v
```

同时需要后端已就绪：至少启动 Gateway(9080)、Auth(9001)、Account(9002)、MySQL、Redis；按使用范围再启动 Settings(9007)、EHR(9006)、CMS(9004)、Portal(9005)、Job(9008)、Forum(9009)、OpenAPI(9010)、File(9011)、Audit(9012)、Meeting(9013)。详见 [后端 README](../polaris-server/README.md)。

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
cd polaris-file-web       # http://localhost:5185
cd polaris-audit-web      # http://localhost:5186
cd polaris-meeting-web    # http://localhost:5187
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

其余应用同理，站点目录建议：`sso`、`ehr`、`settings`、`cms`、`portal`、`job`、`forum`、`admin`、`openapi`、`file`、`audit`、`meeting`。构建产物用新 dist 覆盖旧 dist 前，建议先备份：

```bash
sudo cp -a /opt/polaris/www/openapi /tmp/openapi-dist-bak-$(date +%Y%m%d_%H%M%S)
```

### 2.3 批量构建（PowerShell）

```powershell
$apps = @(
  "polaris-sso-web", "polaris-cms-web", "polaris-portal-web", "polaris-ehr-web",
  "polaris-settings-web", "polaris-job-web", "polaris-forum-web",
  "polaris-forum-admin-web", "polaris-openapi-web", "polaris-file-web",
  "polaris-audit-web", "polaris-meeting-web"
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
| `polaris-meeting-web` | 北极星会（会议室管理） | 5187 | 全员、行政/管理员 |

---

## 四、功能模块

各应用界面预览（生产实测，白天模式，每个菜单一张）。共 **97 张**截图，覆盖全部 12 个前端应用的每个应用 / 模块 / 菜单，完整原图见 [docs/screenshots/](../docs/screenshots/)。

| 统一登录 | 账号中心工作台 | 人力中心-组织树 |
|:---:|:---:|:---:|
| ![统一登录](../docs/screenshots/01-login.png) | ![账号中心](../docs/screenshots/02-sso-dashboard.png) | ![组织树](../docs/screenshots/17-ehr-orgs.png) |

| 内容中心-文章 | 北极星空论坛 | 内容门户 |
|:---:|:---:|:---:|
| ![文章列表](../docs/screenshots/18-cms-articles.png) | ![论坛首页](../docs/screenshots/20-forum-home.png) | ![门户首页](../docs/screenshots/22-portal-home.png) |

| 配置中心-业务点配置 | 论坛管理-抽奖配置 | 人力中心-考勤管理 |
|:---:|:---:|:---:|
| ![业务点配置](../docs/screenshots/51-settings-bizpoints.png) | ![抽奖配置](../docs/screenshots/70-forumadmin-lottery.png) | ![考勤管理](../docs/screenshots/41-ehr-attendance.png) |

| 统一文件服务-概览 | 审计中心-审计事件 | 北极星会-会议室概览 |
|:---:|:---:|:---:|
| ![文件服务](../docs/screenshots/81-file-overview.png) | ![审计事件](../docs/screenshots/88-audit-events.png) | ![北极星会概览](../docs/screenshots/93-meeting-overview.png) |

| 北极星会-位置维护 | 北极星会-会议室预定 | 北极星会-我的预定 |
|:---:|:---:|:---:|
| ![位置维护](../docs/screenshots/94-meeting-locations.png) | ![会议室预定](../docs/screenshots/96-meeting-booking.png) | ![我的预定](../docs/screenshots/97-meeting-mybookings.png) |

**完整截图索引（点击展开各应用）**

<details>
<summary>① 统一账号中心 sso-web（13 张）</summary>

| 登录 | 工作台 | 我的应用 | 审批中心 |
|:---:|:---:|:---:|:---:|
| ![登录](../docs/screenshots/01-login.png) | ![工作台](../docs/screenshots/02-sso-dashboard.png) | ![我的应用](../docs/screenshots/23-sso-myapps.png) | ![审批中心](../docs/screenshots/24-sso-approvals.png) |

| 待办提醒 | 通知中心 | 账户管理 | 角色管理 |
|:---:|:---:|:---:|:---:|
| ![待办提醒](../docs/screenshots/25-sso-messages.png) | ![通知中心](../docs/screenshots/26-sso-notices.png) | ![账户管理](../docs/screenshots/03-sso-account.png) | ![角色管理](../docs/screenshots/27-sso-roles.png) |

| 权限管理 | 应用管理 | 登录日志 | 个人信息 |
|:---:|:---:|:---:|:---:|
| ![权限管理](../docs/screenshots/28-sso-permissions.png) | ![应用管理](../docs/screenshots/29-sso-apps.png) | ![登录日志](../docs/screenshots/30-sso-logs.png) | ![个人信息](../docs/screenshots/31-sso-profile.png) |

| 修改密码 |
|:---:|
| ![修改密码](../docs/screenshots/32-sso-password.png) |

</details>

<details>
<summary>② 人力中心 ehr-web（12 张）</summary>

| 概览 | 组织架构 | 人员管理 | 请假管理 |
|:---:|:---:|:---:|:---:|
| ![概览](../docs/screenshots/04-ehr-dashboard.png) | ![组织架构](../docs/screenshots/17-ehr-orgs.png) | ![人员管理](../docs/screenshots/16-ehr-employees.png) | ![请假管理](../docs/screenshots/33-ehr-leaves.png) |

| 离职申请 | 转正申请 | 审批中心 | 招聘管理 |
|:---:|:---:|:---:|:---:|
| ![离职申请](../docs/screenshots/34-ehr-resign.png) | ![转正申请](../docs/screenshots/35-ehr-regularize.png) | ![审批中心](../docs/screenshots/36-ehr-approvals.png) | ![招聘管理](../docs/screenshots/37-ehr-recruit.png) |

| 通知管理 | 审计日志 | 人力台账 | 考勤管理 |
|:---:|:---:|:---:|:---:|
| ![通知管理](../docs/screenshots/38-ehr-announcements.png) | ![审计日志](../docs/screenshots/39-ehr-auditlogs.png) | ![人力台账](../docs/screenshots/40-ehr-ledgers.png) | ![考勤管理](../docs/screenshots/41-ehr-attendance.png) |

</details>

<details>
<summary>③ 内容中心 cms-web（8 张）</summary>

| 概览 | 内容管理 | 分类管理 | 标签管理 |
|:---:|:---:|:---:|:---:|
| ![概览](../docs/screenshots/05-cms-dashboard.png) | ![内容管理](../docs/screenshots/18-cms-articles.png) | ![分类管理](../docs/screenshots/42-cms-categories.png) | ![标签管理](../docs/screenshots/43-cms-tags.png) |

| 媒体库 | Banner 管理 | 友情链接 | 门户设置 |
|:---:|:---:|:---:|:---:|
| ![媒体库](../docs/screenshots/44-cms-media.png) | ![Banner 管理](../docs/screenshots/45-cms-banners.png) | ![友情链接](../docs/screenshots/46-cms-links.png) | ![门户设置](../docs/screenshots/47-cms-portal.png) |

</details>

<details>
<summary>④ 配置中心 settings-web（10 张）</summary>

| 概览 | 字典配置 | 主数据 | 审批模板 |
|:---:|:---:|:---:|:---:|
| ![概览](../docs/screenshots/06-settings-dashboard.png) | ![字典配置](../docs/screenshots/19-settings-dicts.png) | ![主数据](../docs/screenshots/48-settings-bizdata.png) | ![审批模板](../docs/screenshots/49-settings-approvaltemplates.png) |

| 业务场景 | 业务点配置 | 安全配置 | 消息配置 |
|:---:|:---:|:---:|:---:|
| ![业务场景](../docs/screenshots/50-settings-scenes.png) | ![业务点配置](../docs/screenshots/51-settings-bizpoints.png) | ![安全配置](../docs/screenshots/52-settings-security.png) | ![消息配置](../docs/screenshots/53-settings-notices.png) |

| 埋点配置 | 单号配置 |
|:---:|:---:|
| ![埋点配置](../docs/screenshots/54-settings-embedpoints.png) | ![单号配置](../docs/screenshots/55-settings-coderules.png) |

</details>

<details>
<summary>⑤ 任务调度 job-web（4 张）</summary>

| 任务管理 | 调度日志 | 执行器 | 报警配置 |
|:---:|:---:|:---:|:---:|
| ![任务管理](../docs/screenshots/07-job-dashboard.png) | ![调度日志](../docs/screenshots/56-job-logs.png) | ![执行器](../docs/screenshots/57-job-executors.png) | ![报警配置](../docs/screenshots/58-job-alarms.png) |

</details>

<details>
<summary>⑥ 接口开放平台 openapi-web（8 张）</summary>

| 概览 | API 管理 | 对接应用 | 应用授权 |
|:---:|:---:|:---:|:---:|
| ![概览](../docs/screenshots/08-openapi-dashboard.png) | ![API 管理](../docs/screenshots/09-openapi-endpoints.png) | ![对接应用](../docs/screenshots/10-openapi-apps.png) | ![应用授权](../docs/screenshots/11-openapi-authz.png) |

| 出站渠道 | 调用日志 | 统计报表 | 平台设置 |
|:---:|:---:|:---:|:---:|
| ![出站渠道](../docs/screenshots/12-openapi-channels.png) | ![调用日志](../docs/screenshots/13-openapi-logs.png) | ![统计报表](../docs/screenshots/14-openapi-stats.png) | ![平台设置](../docs/screenshots/15-openapi-settings.png) |

</details>

<details>
<summary>⑦ 论坛管理后台 forum-admin-web（12 张）</summary>

| 概览 | 内容管理 | 评论管理 | 举报管理 |
|:---:|:---:|:---:|:---:|
| ![概览](../docs/screenshots/59-forumadmin-overview.png) | ![内容管理](../docs/screenshots/60-forumadmin-threads.png) | ![评论管理](../docs/screenshots/61-forumadmin-comments.png) | ![举报管理](../docs/screenshots/62-forumadmin-reports.png) |

| 板块与标签 | 站点管理 | 敏感词管理 | 礼品发放 |
|:---:|:---:|:---:|:---:|
| ![板块与标签](../docs/screenshots/63-forumadmin-taxonomy.png) | ![站点管理](../docs/screenshots/64-forumadmin-site.png) | ![敏感词管理](../docs/screenshots/65-forumadmin-sensitive.png) | ![礼品发放](../docs/screenshots/66-forumadmin-exchanges.png) |

| 礼品管理 | 勋章管理 | 等级晋升 | 抽奖配置 |
|:---:|:---:|:---:|:---:|
| ![礼品管理](../docs/screenshots/67-forumadmin-gifts.png) | ![勋章管理](../docs/screenshots/68-forumadmin-badges.png) | ![等级晋升](../docs/screenshots/69-forumadmin-levels.png) | ![抽奖配置](../docs/screenshots/70-forumadmin-lottery.png) |

</details>

<details>
<summary>⑧ 北极星空论坛 forum-web（10 张）</summary>

| 首页 | 抽奖 | 积分中心 | 等级 |
|:---:|:---:|:---:|:---:|
| ![首页](../docs/screenshots/20-forum-home.png) | ![抽奖](../docs/screenshots/21-forum-lottery.png) | ![积分中心](../docs/screenshots/71-forum-points.png) | ![等级](../docs/screenshots/72-forum-levels.png) |

| 兑换礼品 | 勋章 | 关于 | 消息通知 |
|:---:|:---:|:---:|:---:|
| ![兑换礼品](../docs/screenshots/73-forum-gifts.png) | ![勋章](../docs/screenshots/74-forum-badges.png) | ![关于](../docs/screenshots/75-forum-about.png) | ![消息通知](../docs/screenshots/76-forum-notifications.png) |

| 我的收藏 | 个人中心 |
|:---:|:---:|
| ![我的收藏](../docs/screenshots/77-forum-favorites.png) | ![个人中心](../docs/screenshots/78-forum-profile.png) |

</details>

<details>
<summary>⑨ 内容门户 portal-web（3 张）</summary>

| 首页 | 内容列表 | 加入我们 |
|:---:|:---:|:---:|
| ![首页](../docs/screenshots/22-portal-home.png) | ![内容列表](../docs/screenshots/79-portal-list.png) | ![加入我们](../docs/screenshots/80-portal-careers.png) |

</details>

<details>
<summary>⑩ 统一文件服务 file-web（6 张，按菜单）</summary>

| 概览 | 文件管理 | 回收站 |
|:---:|:---:|:---:|
| ![概览](../docs/screenshots/81-file-overview.png) | ![文件管理](../docs/screenshots/82-file-files.png) | ![回收站](../docs/screenshots/83-file-recycle.png) |

| 分享管理 | 存储策略 | 附件设置 |
|:---:|:---:|:---:|
| ![分享管理](../docs/screenshots/84-file-shares.png) | ![存储策略](../docs/screenshots/85-file-policies.png) | ![附件设置](../docs/screenshots/86-file-attachment-settings.png) |

</details>

<details>
<summary>⑪ 审计中心 audit-web（6 张，按菜单）</summary>

| 概览 | 审计事件 | 登录审计 |
|:---:|:---:|:---:|
| ![概览](../docs/screenshots/87-audit-overview.png) | ![审计事件](../docs/screenshots/88-audit-events.png) | ![登录审计](../docs/screenshots/89-audit-login.png) |

| 系统日志 | 日志文件 | 全局设置 |
|:---:|:---:|:---:|
| ![系统日志](../docs/screenshots/90-audit-system-logs.png) | ![日志文件](../docs/screenshots/91-audit-log-files.png) | ![全局设置](../docs/screenshots/92-audit-settings.png) |

</details>

<details>
<summary>⑫ 北极星会 meeting-web（5 张，按菜单）</summary>

| 概览 | 位置维护 | 会议室维护 |
|:---:|:---:|:---:|
| ![会议室概览](../docs/screenshots/93-meeting-overview.png) | ![位置维护](../docs/screenshots/94-meeting-locations.png) | ![会议室维护](../docs/screenshots/95-meeting-rooms.png) |

| 会议室预定 | 我的预定 |
|:---:|:---:|
| ![会议室预定](../docs/screenshots/96-meeting-booking.png) | ![我的预定](../docs/screenshots/97-meeting-mybookings.png) |

</details>

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

| 概览 | API 管理 | 对接应用 | 应用授权 |
|:---:|:---:|:---:|:---:|
| ![概览](../docs/screenshots/08-openapi-dashboard.png) | ![API 管理](../docs/screenshots/09-openapi-endpoints.png) | ![对接应用](../docs/screenshots/10-openapi-apps.png) | ![应用授权](../docs/screenshots/11-openapi-authz.png) |

| 出站渠道 | 调用日志 | 统计报表 | 平台设置 |
|:---:|:---:|:---:|:---:|
| ![出站渠道](../docs/screenshots/12-openapi-channels.png) | ![调用日志](../docs/screenshots/13-openapi-logs.png) | ![统计报表](../docs/screenshots/14-openapi-stats.png) | ![平台设置](../docs/screenshots/15-openapi-settings.png) |

- **概览 `DashboardView`**：状态指标卡片、调用趋势、失败 TOP、最近异常；表格/详情统一用抽屉展示；方向按数据流向标注（出站=对外提供、入站=调用第三方）。
- **API 管理 `EndpointView`**：注册/维护开放接口，配置方向、操作类型、权限码、限流、幂等、状态；启用状态的接口不能删除，只能停用。
- **对接应用 `AppView`**：维护第三方应用（appCode/名称/状态/IP 白名单）；新增/查看密钥/重置密钥（密钥抽屉区分查看/重置/新建三种模式）；有接口授权的应用「删除」按钮置灰并提示，后端同时兜底拦截。
- **应用授权 `AuthzView`**：按接口粒度勾选授权（唯一键 `(app_code, api_code)`），支持停用/启用；停用/删除单个接口不联动其他接口；顶部可按应用筛选。
- **出站渠道 `ChannelView`**：配置平台调用外部的渠道（目前邮件 SMTP）的鉴权方式、超时、重试、允许应用与凭证托管；凭证只回显「是否已配置」，不明文展示。
- **调用日志 `LogView`**：Trace ID、方向、**调用方向**（箭头直观展示「应用→平台」或「平台→渠道」）、应用/API/渠道、HTTP 状态、耗时、时间；详情抽屉含三张卡片；支持按条件筛选与导出。
- **统计报表 `StatView`**：按方向/应用/时间统计调用量、成功率、耗时分布、TOP 应用与接口。
- **平台设置 `SettingView`**：令牌有效期、签名容差、限流等全局参数。

页面统一遵循：弹窗改抽屉、分页固定右下角、空状态铺满、操作列左对齐不换行、确认框居中；详情见 `src/styles/global.css` 的 `.drawer-body-flex`。

### 4.8 统一文件服务（`polaris-file-web`）

端口 5185，生产域名 `polaris-file.laimengchao.site`，对接后端 file-service（:9011）。6 个功能页：

- **概览 `DashboardView`**：文件总数、公开/私有占比、占用空间、近 10 天上传统计与按存储策略分布。
- **文件管理 `FilesView`**：文件列表、上传（含秒传与分片）、预览、下载、分享；文件名列长文本省略 + 悬浮展示完整值，回收站操作在行内。
- **回收站 `RecycleView`**：标记删除文件列表，支持恢复与彻底删除。
- **分享管理 `SharesView`**：分享链接、提取码、有效期与访问次数管理。
- **存储策略 `PoliciesView`**：按业务类型配置可见性（PUBLIC/PRIVATE）、大小上限、允许扩展名与启停。
- **附件设置 `AttachmentSettingsView`**：按业务类型配置水印内容、字号/颜色/透明度、作用文件类型与生效环节（在线预览/分享访问/下载），改动保存后即时生效。

### 4.9 审计中心（`polaris-audit-web`）

端口 5186，生产域名 `polaris-audit.laimengchao.site`，对接后端 audit-server（:9012）。6 个功能页：

- **概览 `DashboardView`**：审计事件与系统日志趋势、来源应用分布、登录与敏感操作统计。
- **审计事件 `EventsView`**：全量事件查询（操作人/应用/类别/结果/日期）、入参与响应详情抽屉、CSV 导出（按钮挂 `audit:events:export`，导出行为后端强制留痕）。长文本列统一 `cell-ellipsis` + `a-tooltip` 悬浮看全文。
- **登录审计 `LoginAuditView`**：登录成功/失败记录，失败原因与客户端列宽内省略 + 悬浮完整值。
- **系统日志 `SystemLogsView`**：common Logback Appender 采集的 WARN/ERROR 日志查询。
- **日志文件 `LogFilesView`**：服务日志文件浏览与下载。
- **全局设置 `SettingView`**：采集/忽略/留存/脱敏隐私/导出合规五个分区，按 `audit:setting:*` 分区权限显示，保存/放弃/恢复默认。

### 4.10 北极星会（`polaris-meeting-web`）

端口 5187，生产域名 `https://poraris-meeting.laimengchao.site/`，对接后端 meeting-service（:9013）。面向全员提供会议室预定，面向行政/管理员提供位置与房间台账维护，共 5 个功能页：

| 概览 | 位置维护 | 会议室维护 |
|:---:|:---:|:---:|
| ![会议室概览](../docs/screenshots/93-meeting-overview.png) | ![位置维护](../docs/screenshots/94-meeting-locations.png) | ![会议室维护](../docs/screenshots/95-meeting-rooms.png) |

| 会议室预定 | 我的预定 |
|:---:|:---:|
| ![会议室预定](../docs/screenshots/96-meeting-booking.png) | ![我的预定](../docs/screenshots/97-meeting-mybookings.png) |

- **概览 `OverviewView`**：按日期展示全平台当日会议、待签到、进行中、可用会议室数；月历与日程按「本人创建 + 被邀请」口径联动，支持按可选日期范围翻月查看。
- **位置维护 `LocationView`**：城市取平台字典只读；按城市维护大楼与楼层，实时统计会议室数；已有关联会议室的大楼/楼层禁止删除或停用。
- **会议室维护 `RoomsView`**：维护房间类型、容量、设备、图片与状态；支持城市/大楼/楼层/类型/设备/状态组合筛选；维护、停用房间不可预约。
- **会议室预定 `BookingView`**：按当前人员城市过滤房间，24 小时 30 分钟粒度时间轴展示占用与过去时段；弹窗点选起止格、选参会人并提交，前端提前拦截冲突、跨天、超限时长与禁约。
- **我的预定 `MyBookingsView`**：已预约 / 已开始 / 已结束 / 已取消四页签，页签角标与筛选条件同步；支持详情、修改、签到、延时、取消与释放。
- **规则配置**：可预约天数、单次最长时长、未签到释放宽限期、爽约限制均由 settings 埋点配置，前端从会议服务读取并展示，服务端做最终校验。

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
| `VITE_SSO_ORIGIN` | 开发 `http://localhost:5173`；生产建议显式注入 `https://polaris-sso.laimengchao.site` | 账号中心地址，用于跳转登录；未注入时生产构建回退到正式域名，不再回退 localhost |

```powershell
$env:VITE_SSO_TARGET = "http://127.0.0.1:9080"
$env:VITE_SSO_ORIGIN = "http://localhost:5173"
npm run dev
```

变量在 Vite 配置阶段读取，修改后需重启；前端变量会进入浏览器产物，**不要放数据库密码、appSecret、JWT 私钥或内部 token**。

本地推荐地址（不要混用 `localhost` 与 `127.0.0.1`，它们是不同 origin）：

```text
账号中心 5173 / CMS 5177 / Portal 5178 / EHR 5179 / Settings 5180
Job 5181 / 论坛前台 5182 / 论坛管理 5183 / 开放平台 5184
文件服务 5185 / 审计中心 5186 / 会议室管理 5187 / Gateway 9080
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
