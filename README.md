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

## 快速开始

```bash
git clone https://github.com/crom-lmc/polaris.git
cd polaris
```

- [后端：从 clone 到启动 / 上线](polaris-server/README.md#一从-clone-到启动本地部署) — JDK 17、Maven 3.9+、MySQL、Redis；建库导 SQL、配密钥与环境变量、按序启动服务；生产 jar 发布与 Docker/Nginx。
- [前端：从 clone 到启动 / 上线](polaris-web/README.md#一从-clone-到启动本地部署) — Node.js 20、各应用 `npm ci && npm run dev`；生产 `npm run build` 后发布 dist 到 Nginx。

## 平台能力

统一账号与 SSO、RBAC 与组织数据范围、EHR 人力、配置审批与消息、CMS 内容、门户聚合、调度中心、论坛，以及**接口开放平台**（第三方换票调用平台接口、出站渠道调用外部服务、调用日志与统计）。

## 约定

- 后端各服务按业务分库；Gateway(9080) 是统一入口。
- 生产 `sql.init.mode=never`，密钥只用环境变量或 `ENC(...)` 注入，绝不入库。
- 新增功能按前后端 README 的「开发规范」执行；迭代文档放仓库外，不纳入 Git。
