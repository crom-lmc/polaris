# Polaris 一体化企业平台

前后端一体化仓库。一次 clone 即可拿到完整源码：后端 `polaris-server/`、前端 `polaris-web/`。

## 仓库结构

```text
polaris/
├── README.md            # 本文件：总览与快速入口
├── polaris-server/      # 后端：Java 17 + Spring Boot 3 + Spring Cloud，多模块 Maven
│   └── README.md        # 后端说明：本地部署、生产部署、模块、环境变量、FAQ
└── polaris-web/         # 前端：Vue 3 + TypeScript + Vite，9 个独立应用
    └── README.md        # 前端说明：本地部署、生产部署、应用与端口、FAQ
```

## 界面预览

以下截图均来自生产环境（[polaris-sso.laimengchao.site](https://polaris-sso.laimengchao.site/)）。共 **80 张**，覆盖全部 9 个前端应用的每个应用 / 模块 / 菜单，原图见 [docs/screenshots/](docs/screenshots/)。

**快速预览**

| 统一登录 | 账号中心工作台 | 人力中心-组织树 |
|:---:|:---:|:---:|
| ![统一登录](docs/screenshots/01-login.png) | ![工作台](docs/screenshots/02-sso-dashboard.png) | ![组织树](docs/screenshots/17-ehr-orgs.png) |

| 接口开放平台-概览 | 应用授权 | 调用日志 |
|:---:|:---:|:---:|
| ![开放平台概览](docs/screenshots/08-openapi-dashboard.png) | ![应用授权](docs/screenshots/11-openapi-authz.png) | ![调用日志](docs/screenshots/13-openapi-logs.png) |

| 内容中心-文章 | 北极星空论坛 | 内容门户 |
|:---:|:---:|:---:|
| ![文章列表](docs/screenshots/18-cms-articles.png) | ![论坛首页](docs/screenshots/20-forum-home.png) | ![门户首页](docs/screenshots/22-portal-home.png) |

| 配置中心-业务点配置 | 论坛管理-抽奖配置 | 人力中心-考勤管理 |
|:---:|:---:|:---:|
| ![业务点配置](docs/screenshots/51-settings-bizpoints.png) | ![抽奖配置](docs/screenshots/70-forumadmin-lottery.png) | ![考勤管理](docs/screenshots/41-ehr-attendance.png) |

**完整截图索引（点击展开各应用）**

<details>
<summary>① 统一登录中心 sso-web（13 张）</summary>

| 登录 | 工作台概览 | 我的应用 | 审批中心 |
|:---:|:---:|:---:|:---:|
| ![登录](docs/screenshots/01-login.png) | ![概览](docs/screenshots/02-sso-dashboard.png) | ![我的应用](docs/screenshots/23-sso-myapps.png) | ![审批中心](docs/screenshots/24-sso-approvals.png) |

| 待办提醒 | 通知中心 | 账户管理 | 角色管理 |
|:---:|:---:|:---:|:---:|
| ![待办提醒](docs/screenshots/25-sso-messages.png) | ![通知中心](docs/screenshots/26-sso-notices.png) | ![账户管理](docs/screenshots/03-sso-account.png) | ![角色管理](docs/screenshots/27-sso-roles.png) |

| 权限管理 | 应用管理 | 登录日志 | 个人信息 |
|:---:|:---:|:---:|:---:|
| ![权限管理](docs/screenshots/28-sso-permissions.png) | ![应用管理](docs/screenshots/29-sso-apps.png) | ![登录日志](docs/screenshots/30-sso-logs.png) | ![个人信息](docs/screenshots/31-sso-profile.png) |

| 修改密码 |
|:---:|
| ![修改密码](docs/screenshots/32-sso-password.png) |

</details>

<details>
<summary>② 人力中心 ehr-web（12 张）</summary>

| 概览 | 组织架构 | 人员管理 | 请假管理 |
|:---:|:---:|:---:|:---:|
| ![概览](docs/screenshots/04-ehr-dashboard.png) | ![组织架构](docs/screenshots/17-ehr-orgs.png) | ![人员管理](docs/screenshots/16-ehr-employees.png) | ![请假管理](docs/screenshots/33-ehr-leaves.png) |

| 离职申请 | 转正申请 | 审批中心 | 招聘管理 |
|:---:|:---:|:---:|:---:|
| ![离职申请](docs/screenshots/34-ehr-resign.png) | ![转正申请](docs/screenshots/35-ehr-regularize.png) | ![审批中心](docs/screenshots/36-ehr-approvals.png) | ![招聘管理](docs/screenshots/37-ehr-recruit.png) |

| 通知管理 | 审计日志 | 人力台账 | 考勤管理 |
|:---:|:---:|:---:|:---:|
| ![通知管理](docs/screenshots/38-ehr-announcements.png) | ![审计日志](docs/screenshots/39-ehr-auditlogs.png) | ![人力台账](docs/screenshots/40-ehr-ledgers.png) | ![考勤管理](docs/screenshots/41-ehr-attendance.png) |

</details>

<details>
<summary>③ 内容中心 cms-web（8 张）</summary>

| 概览 | 内容管理 | 分类管理 | 标签管理 |
|:---:|:---:|:---:|:---:|
| ![概览](docs/screenshots/05-cms-dashboard.png) | ![内容管理](docs/screenshots/18-cms-articles.png) | ![分类管理](docs/screenshots/42-cms-categories.png) | ![标签管理](docs/screenshots/43-cms-tags.png) |

| 媒体库 | Banner 管理 | 友情链接 | 门户设置 |
|:---:|:---:|:---:|:---:|
| ![媒体库](docs/screenshots/44-cms-media.png) | ![Banner 管理](docs/screenshots/45-cms-banners.png) | ![友情链接](docs/screenshots/46-cms-links.png) | ![门户设置](docs/screenshots/47-cms-portal.png) |

</details>

<details>
<summary>④ 配置中心 settings-web（10 张）</summary>

| 概览 | 字典配置 | 主数据 | 审批模板 |
|:---:|:---:|:---:|:---:|
| ![概览](docs/screenshots/06-settings-dashboard.png) | ![字典配置](docs/screenshots/19-settings-dicts.png) | ![主数据](docs/screenshots/48-settings-bizdata.png) | ![审批模板](docs/screenshots/49-settings-approvaltemplates.png) |

| 业务场景 | 业务点配置 | 安全配置 | 消息配置 |
|:---:|:---:|:---:|:---:|
| ![业务场景](docs/screenshots/50-settings-scenes.png) | ![业务点配置](docs/screenshots/51-settings-bizpoints.png) | ![安全配置](docs/screenshots/52-settings-security.png) | ![消息配置](docs/screenshots/53-settings-notices.png) |

| 埋点配置 | 单号配置 |
|:---:|:---:|
| ![埋点配置](docs/screenshots/54-settings-embedpoints.png) | ![单号配置](docs/screenshots/55-settings-coderules.png) |

</details>

<details>
<summary>⑤ 任务调度 job-web（4 张）</summary>

| 任务管理 | 调度日志 | 执行器 | 报警配置 |
|:---:|:---:|:---:|:---:|
| ![任务管理](docs/screenshots/07-job-dashboard.png) | ![调度日志](docs/screenshots/56-job-logs.png) | ![执行器](docs/screenshots/57-job-executors.png) | ![报警配置](docs/screenshots/58-job-alarms.png) |

</details>

<details>
<summary>⑥ 接口开放平台 openapi-web（8 张）</summary>

| 概览 | API 管理 | 对接应用 | 应用授权 |
|:---:|:---:|:---:|:---:|
| ![概览](docs/screenshots/08-openapi-dashboard.png) | ![API 管理](docs/screenshots/09-openapi-endpoints.png) | ![对接应用](docs/screenshots/10-openapi-apps.png) | ![应用授权](docs/screenshots/11-openapi-authz.png) |

| 出站渠道 | 调用日志 | 统计报表 | 平台设置 |
|:---:|:---:|:---:|:---:|
| ![出站渠道](docs/screenshots/12-openapi-channels.png) | ![调用日志](docs/screenshots/13-openapi-logs.png) | ![统计报表](docs/screenshots/14-openapi-stats.png) | ![平台设置](docs/screenshots/15-openapi-settings.png) |

</details>

<details>
<summary>⑦ 论坛管理后台 forum-admin-web（12 张）</summary>

| 概览 | 内容管理 | 评论管理 | 举报管理 |
|:---:|:---:|:---:|:---:|
| ![概览](docs/screenshots/59-forumadmin-overview.png) | ![内容管理](docs/screenshots/60-forumadmin-threads.png) | ![评论管理](docs/screenshots/61-forumadmin-comments.png) | ![举报管理](docs/screenshots/62-forumadmin-reports.png) |

| 板块与标签 | 站点管理 | 敏感词管理 | 礼品发放 |
|:---:|:---:|:---:|:---:|
| ![板块与标签](docs/screenshots/63-forumadmin-taxonomy.png) | ![站点管理](docs/screenshots/64-forumadmin-site.png) | ![敏感词管理](docs/screenshots/65-forumadmin-sensitive.png) | ![礼品发放](docs/screenshots/66-forumadmin-exchanges.png) |

| 礼品管理 | 勋章管理 | 等级晋升 | 抽奖配置 |
|:---:|:---:|:---:|:---:|
| ![礼品管理](docs/screenshots/67-forumadmin-gifts.png) | ![勋章管理](docs/screenshots/68-forumadmin-badges.png) | ![等级晋升](docs/screenshots/69-forumadmin-levels.png) | ![抽奖配置](docs/screenshots/70-forumadmin-lottery.png) |

</details>

<details>
<summary>⑧ 北极星空论坛 forum-web（10 张）</summary>

| 首页 | 抽奖 | 积分中心 | 等级 |
|:---:|:---:|:---:|:---:|
| ![首页](docs/screenshots/20-forum-home.png) | ![抽奖](docs/screenshots/21-forum-lottery.png) | ![积分中心](docs/screenshots/71-forum-points.png) | ![等级](docs/screenshots/72-forum-levels.png) |

| 兑换礼品 | 勋章 | 关于 | 消息通知 |
|:---:|:---:|:---:|:---:|
| ![兑换礼品](docs/screenshots/73-forum-gifts.png) | ![勋章](docs/screenshots/74-forum-badges.png) | ![关于](docs/screenshots/75-forum-about.png) | ![消息通知](docs/screenshots/76-forum-notifications.png) |

| 我的收藏 | 个人中心 |
|:---:|:---:|
| ![我的收藏](docs/screenshots/77-forum-favorites.png) | ![个人中心](docs/screenshots/78-forum-profile.png) |

</details>

<details>
<summary>⑨ 内容门户 portal-web（3 张）</summary>

| 首页 | 内容列表 | 加入我们 |
|:---:|:---:|:---:|
| ![首页](docs/screenshots/22-portal-home.png) | ![内容列表](docs/screenshots/79-portal-list.png) | ![加入我们](docs/screenshots/80-portal-careers.png) |

</details>

## 快速开始

```bash
git clone https://github.com/crom-lmc/polaris.git
cd polaris
```

- [后端：从 clone 到启动 / 上线](polaris-server/README.md#一从-clone-到启动本地部署) — JDK 17、Maven 3.9+、MySQL、Redis；建库导 SQL、配密钥与环境变量、按序启动服务；生产 jar 发布与 Docker/Nginx。
- [前端：从 clone 到启动 / 上线](polaris-web/README.md#一从-clone-到启动本地部署) — Node.js 20、各应用 `npm ci && npm run dev`；生产 `npm run build` 后发布 dist 到 Nginx。

## 平台能力

平台由 8 个业务域 + 1 个开放平台组成，每个域都有独立的后端服务与前端应用，经 Gateway 统一接入、共享 SSO 登录与 RBAC 权限体系。

| 业务域 | 后端服务（端口） | 前端应用（开发端口） | 核心能力 |
|---|---|---|---|
| 统一账号与 SSO | gateway 9080 / auth 9001 / account 9002 | `polaris-sso-web` (5173) | 登录/验证码/单点登录、RS256 令牌签发与吊销、账号/角色/权限码、应用登记与授权、组织数据范围、审批中心、登录日志 |
| 人力中心 EHR | ehr 9006 | `polaris-ehr-web` (5179) | 组织树、员工档案、合同/司龄/年假、考勤与假勤、招聘、转正离职、审批协同、公告、人力台账 |
| 配置与审批中心 | settings 9007 | `polaris-settings-web` (5180) | 数据字典、单号规则、主数据、审批流程与场景、统一待办已办、站内消息与安全配置 |
| 内容中心 CMS | cms 9004 | `polaris-cms-web` (5177) | 文章草稿/审批/发布、Markdown 编辑、树形栏目、Banner/推荐位、媒体与友链 |
| 内容门户 Portal | portal 9005 | `polaris-portal-web` (5178) | 聚合 CMS 公开内容、统一待办、招聘展示、门户首页与文章详情 |
| 调度中心 Job | job 9008 | `polaris-job-web` (5181) | 任务定义与 Cron、执行器与 Handler、启停/触发、执行日志与失败报警 |
| 论坛 Forum | forum 9009 | `polaris-forum-web` (5182) / `polaris-forum-admin-web` (5183) | 板块/标签/帖子、积分等级徽章、抽奖（九宫格/转盘）与礼品兑换、内容审核与活动运营 |
| **接口开放平台** | openapi 9010（独立库 `polaris_openapi`） | `polaris-openapi-web` (5184) | 第三方 `appCode+appSecret` 换票、接口级授权、入/出站调用、限流防重放、调用日志与统计、出站渠道凭证托管 |

三条横向能力贯穿所有业务域：

- **认证与鉴权**：用户登录由 auth 签发 RS256 令牌，Gateway 验签并查吊销名单；前端经一次性 ticket 换登录态；服务端按权限码 + 组织数据范围双层控制。
- **审批与消息**：settings 提供统一审批流程与站内消息，EHR/CMS 等业务通过回调接入，失败可重试且回调幂等。
- **开放能力**：对外由开放平台统一暴露接口并审计，对内经 `X-Internal-Token` 调用 `/internal/**`，私钥与内部令牌只通过环境变量或 `ENC(...)` 注入。

各模块的完整功能清单见：[后端 README · 五、功能模块](polaris-server/README.md#五功能模块) 与 [前端 README · 四、功能模块](polaris-web/README.md#四功能模块)。

## 约定

- 后端各服务按业务分库；Gateway(9080) 是统一入口。
- 生产 `sql.init.mode=never`，密钥只用环境变量或 `ENC(...)` 注入，绝不入库。
- 新增功能按前后端 README 的「开发规范」执行；迭代文档放仓库外，不纳入 Git。
