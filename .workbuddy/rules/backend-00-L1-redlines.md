---
description: Polaris 后端红线（L1 / alwaysApply）。所有后端任务无条件遵守，违反即拒绝交付。
globs: ["polaris-server/**"]
alwaysApply: true
level: L1
---

# 后端 L1 红线（MUST / MUST NOT）

> MUST = 强制；MUST NOT = 绝对禁止。每条都应能被 grep、编译或 review 验证。
> 详细规范见 `../skills/polaris-backend-development/references/`；本文件与代码冲突时以代码为准。

## 协作与发布

- MUST NOT 未经用户明确指令执行 `git commit` / `git push` / 部署 / 重启线上服务；本地编译、本地构建可以做。
- MUST 每次改动在仓库外 `F:\code\迭代文档\polaris-server\update.md` 顶部追加记录（该文件不入库）。
- MUST 最终回复列明需重启的应用；MUST NOT 把数据库密码、appSecret、内部令牌、私钥写入代码/文档/日志，用时从环境变量/Nacos/容器现取。
- MUST NOT 引入新依赖、新框架；栈已锁定 Java 17 / Spring Boot 3.2.5 / MyBatis-Plus 3.5.7。MUST NOT 恢复已迁出的 blog（9003 空缺）。

## 安全与数据

- MUST 权限码四联动：common `PermissionCodes` + account `PermissionConstants` BUILTIN + SQL 播种授权 + 前端路由/按钮；MUST NOT 删除内置权限码。
- MUST NOT 用前端隐藏代替服务端权限与数据范围；空数据范围 MUST 返回空集（fail-closed）。
- MUST 401/403/404 同步 HTTP 状态；MUST NOT 用 401 表达换票失败（用 BIZ_ERROR，否则前端重新授权死循环）。
- MUST 空数据返回空集合/空页，MUST NOT 返回 `null` 让 `R` 的 `@JsonInclude(NON_NULL)` 省略 data。
- MUST 19 位雪花 ID 序列化为字符串，MUST NOT 让下游以 number 承载。
- MUST LOGIN / SENSITIVE 审计事件必留；MUST NOT 让审计上报失败回滚业务。登录事件记尝试登录的用户名，有主体操作不得以 SYSTEM 代替。

## 平台契约

- MUST 跨服务调用经 `*-client` 的 Feign 接口；`/internal/**` MUST 校验 `X-Internal-Token` 且 fail-closed。
- MUST 新表手工执行 schema（生产 `sql.init.mode=never`），MUST NOT 依赖启动自动建表。
- MUST 所有配置类数据只在 settings 一处维护，MUST NOT 写死在服务本地表或代码常量。
- MUST 匿名 `/public/**` 不在审计范围；登录后才能做的动作 MUST 全审计。

## 判断纪律

- MUST 先读代码或全仓 grep 再判断能力归属，MUST NOT 望文生义（forum.notification 非通知通道、system_log 由 common 采集、ehr.announcement 非平台公告）。
