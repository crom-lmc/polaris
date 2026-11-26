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
- MUST 提交备注（commit message）用中文，MUST NOT 使用英文 subject（如 `feat(xxx): launch ...`）；格式用 `类型(范围): 中文概述`，例如 `feat(finance): 报销接入审批引擎`、`fix(settings): 修复字典项 ID 冲突`。
- MUST 前后端分开提交：一次任务同时改动 `polaris-server/` 与 `polaris-web/` 时，必须拆成至少两个 commit——后端代码及其平台侧配套（SQL / Nacos / 网关 / 部署配置）走一个后端提交，前端代码走一个前端提交；MUST NOT 混在一个 commit 里。只改一端时按实际范围提交即可。
- MUST 生产后端发布采用滚动重启：一次只重启 1 个服务，确认该服务 healthy/running 后再继续下一个，服务间隔 30 秒；MUST NOT 用 `docker compose up -d` 或一次性重启/拉起全部服务（4G 机器多容器冷启动会触发 swap 抖动甚至卡死）。使用 `deploy/rolling-restart.sh` 执行，失败立即停止，不继续后续服务。
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

## 分层结构（Service）

- MUST 所有业务 Service 采用「接口 + 实现类」结构：`XxxService` 是 `interface`（只放方法签名），实现类放同级 `impl/` 子包、命名 `XxxServiceImpl` 并标注 `@Service`、`implements XxxService`。
- MUST Controller / 其他 Service / Job 等调用方一律注入接口类型 `XxxService`，MUST NOT 注入 `XxxServiceImpl`；静态工具方法（无 Spring 状态）可留在接口或独立工具类，不强制建 impl。
- MUST NOT 在 `XxxService` 接口里写方法实现体或 `@Service`；接口方法 MUST NOT 显式写 `public`（接口方法默认 public）；实现类 MUST NOT 再声明接口已定义的方法签名以外的 public 业务入口而不在接口暴露。
- MUST 常量 / 变量只放在 `XxxServiceImpl`（含 `static {}` 初始化的字段），接口 MUST NOT 声明常量；调用方引用常量时用 `XxxServiceImpl.CONST`，不要回退到 `XxxService.CONST`。
- MUST 接口只保留正文实际引用的 import，MUST NOT 保留 IDE 显示的灰色未引用 import（如 mapper / entity / Lombok / Spring 注解残留）。
- `ContractSupport` / `*Utils` / `*Preset` / `*Calculator` 等静态工具类，以及 `ApprovalEngine` / `NoticeMailSender` 等非 `*Service` 命名助手组件，不属于 `XxxService` 接口+实现改造范围，保持原样即可。
- 存量单 class Service MUST 逐步按此结构拆分，新增 Service 从一开始就必须按此结构创建。

## 平台契约

- MUST 跨服务调用经 `*-client` 的 Feign 接口；`/internal/**` MUST 校验 `X-Internal-Token` 且 fail-closed。
- MUST 所有附件/图片的**上传与预览**都走统一文件服务：上传经 `FileApi.upload`、业务表只存 `file_id`、预览经 `FileApi.sign` 出临时 URL；MUST NOT 自建附件表、本地磁盘落盘、`MultipartFile` 自写保存、base64 落库或本地静态资源映射回退（含"统一服务失败降级本地"）。存量本地回退只允许读取历史数据，新增上传路径 MUST 无条件走文件服务。
- MUST 新增上传业务时，`bizType` 必须在 `file_storage_policy` 有对应启用策略（file-service 按 bizType 精确匹配且无兜底，缺策略上传直接 8102）；MUST 同步在 `file-service/src/main/resources/data.sql` 加种子并在 `deploy/sql/NN-*.sql` 加生产迁移，两者 `id` 一致。MUST NOT 只在代码写 bizType 而不登记策略，也 MUST NOT 复用其它业务的 bizType 绕过。
- MUST 新表手工执行 schema（生产 `sql.init.mode=never`），MUST NOT 依赖启动自动建表。
- MUST 所有配置类数据只在 settings 一处维护，MUST NOT 写死在服务本地表或代码常量。
- MUST 匿名 `/public/**` 不在审计范围；登录后才能做的动作 MUST 全审计。

## 判断纪律

- MUST 先读代码或全仓 grep 再判断能力归属，MUST NOT 望文生义（forum.notification 非通知通道、system_log 由 common 采集、ehr.announcement 非平台公告）。
