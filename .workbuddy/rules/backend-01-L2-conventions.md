---
description: Polaris 后端 L2 规范，编辑 polaris-server/** 时加载。约束分层、响应、权限、SQL、跨服务调用与审计的可验证写法。
globs: ["polaris-server/**"]
alwaysApply: false
level: L2
---

# 后端 L2 规范（polaris-server/**）

> 从现有代码实证提炼；以本服务既有写法为准。L1 红线见 `00-L1-redlines.md`。

## 分层与注入

- MUST 用 `Controller → 自定义 XxxService 接口 → XxxServiceImpl → BaseMapper`，构造器注入（`@RequiredArgsConstructor` + `private final`）。
- MUST NOT 使用 MyBatis-Plus 的 `IService` / `ServiceImpl`（全仓未发现）；MUST NOT 用字段注入。
- MUST 查询用 `LambdaQueryWrapper` / `LambdaUpdateWrapper`；导出与分页 MUST 共用同一个 wrapper 构造方法。
- MUST 多个 `like...or()` 用 `w.nested(...)` 包裹，避免后续 `in()` 因 AND 优先级失效。
- MUST 写方法加 `@Transactional(rollbackFor = Exception.class)`；预期业务失败用 `log.warn`，未捕获异常用 `log.error` + 业务错误码，MUST NOT 吞异常。

## 响应与状态码

- MUST 返回 `R.ok(data)` / `PageResult`，分页壳与本服务既有接口一致（`PageResult{records,...}` 或 `R.Page{list,total}`），新增接口前先确认调用方口径。
- MUST 业务异常抛 `BizException`，MUST NOT 在 Controller 手动 `R.fail()` 后继续执行。
- MUST 启动类 `scanBasePackages` 含 `com.laimengchao.sso.common`（网关是唯一例外，刻意不扫）。

## 权限与身份

- MUST 按本服务 Aspect 的 app 域口径写 `@Permit`：audit 多值 `app[]`、file/forum 单值、cms/ehr/job/settings/openapi 注解无 `app()` 由 Aspect 硬编码。
- MUST `SsoHeaderAuthFilter` 在 finally 清理 `SecurityContextHolder` / `AccountContext`；网关 MUST 先剥离客户端自带的 `X-Account-*` 头。
- MUST 权限码与前端路由/按钮使用同一字符串；内置权限码不可删，只能改名或改排序。

## 跨服务调用：向其他服务提供接口（对外暴露能力）

> 本平台的服务间调用**不经网关**，通过 `*-client` 模块的 Feign 接口直连目标服务端口。现有 6 个 client：`audit / ehr / file / job / settings / sso`。

**何时建新 client（决策）**

- MUST 当某个服务需要**向其他服务提供能力**时，把接口契约收敛到独立的 `polaris-<域>-client` 模块，由调用方依赖该模块；MUST NOT 让调用方直接依赖服务方实现模块，也 MUST NOT 在调用方自建 Feign 重复声明同一能力。
- MUST 同一服务域新增接口时**复用**该域已有 client（在其 `api/` 下加接口类并注册到 `@EnableFeignClients`），MUST NOT 为新增一个接口就新建一个 client 模块。实例：settings-client 一个域内有 12 个 Api 共用 base-url、靠不同 `contextId` 隔离。
- MUST 能力归属变化（如审计上报从账号域迁出）时同步迁移 client 契约并在 AutoConfiguration 注释中说明，MUST NOT 让旧契约与新契约并存。

**新建 client 的标准结构（逐步，缺一不可）**

1. 模块 `polaris-<域>-client`，pom 依赖 openfeign + autoconfigure + jackson + slf4j + lombok(optional)，**刻意不依赖 common**，自带一份 `dto/R.java` 响应壳；并在根 `pom.xml` 的 `dependencyManagement` 登记。
2. `config/XxxFeignConfig.java`：`@Value("${<域>.client.internal-token:${SSO_INTERNAL_TOKEN:}}")` + `@Bean RequestInterceptor` 注入 `X-Internal-Token` + `@Bean Request.Options` 显式设超时；**MUST NOT 标 `@Configuration`**（会升级为全局配置污染其他 client）。
3. `api/XxxApi.java`：`@FeignClient(name, contextId, url 三级默认, configuration)`，方法路径写全 `/internal/**`；同一域多接口靠不同 `contextId` 隔离。
4. `XxxClientAutoConfiguration.java`：`@AutoConfiguration` + `@EnableFeignClients(clients = {...})`。
5. `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 写入全限定类名，调用方引入 jar 即自动装配。

**服务端暴露与鉴权**

- MUST 服务端建 `Internal*Controller`，`@RequestMapping("/internal/**")`，统一返回 `R.ok(...)`；方法语义与 client 的 Api 一一对应。
- MUST `/internal/**` 的鉴权交给 common 的 `InternalTokenInterceptor`（由 `CommonSecurityWebConfig` 按 `addPathPatterns("/internal/**")` 注册），服务端 `SecurityConfig` 对其 `permitAll`；MUST NOT 在每个 Controller 里各写一遍 token 校验。
- MUST 该拦截器 fail-closed：令牌未配置返回 **503**、令牌不匹配返回 **401**，且用 `MessageDigest.isEqual` 常量时间比较；MUST NOT 在 token 为空时放行。
- MUST 网关先剥离客户端自带的 `X-Internal-Token` 与 `X-Account-*` 头；对外部请求访问 `/api/auth/internal/**` 直接 403。
- MUST 调用方显式判 `resp != null && resp.ok() && resp.getData() != null` 再取值；token 未配置时客户端拦截器空转、请求无令牌，服务端会 401/503，调用方 MUST 按业务语义兜底（读接口返空集，关键链路 fail-closed 抛业务错误）。

**契约与精度坑**

- MUST 雪花 ID 在 client 的 DTO 与请求体中用 **String** 承载（服务端 `@JsonSerialize(ToStringSerializer.class)`），MUST NOT 用 Long/number 跨服务传 19 位 ID。
- MUST 需要保留原始 JSON（含字符串化 ID）时用 `*Raw` 方法返回 `R<JsonNode>`，MUST NOT 在 client 复制服务器 VO 后漏写序列化注解导致精度丢失。
- MUST 显式设 `Request.Options` 超时（现有档位 3s 连接 + 5~30s 读，按链路性质选）；MUST NOT 依赖 Feign 默认 60s。
- MUST 新业务定时任务实现 `JobHandler` 走调度中心；本地 `@Scheduled` 仅保留少量清理任务。例外：`job-client` 是应用侧 SDK（无 `@FeignClient`、无 `R`），中心派发走 `/internal/jobs/run` 并额外校验 `X-Job-Token`，不要照搬 Feign 模板。

## 数据库与 SQL

- MUST `schema.sql` 用 `CREATE TABLE IF NOT EXISTS`、`data.sql` 用 `INSERT IGNORE`，空脚本留 `SELECT 1;`。
- MUST `deploy/sql/NN-*` 编号递增、幂等，新变更新增脚本不改历史脚本。
- MUST 存量补列走 `*SchemaInitializer`（查 `information_schema` 后 ALTER，失败只 warn），因 MySQL 5.7 不支持 `ADD COLUMN IF NOT EXISTS`。
- MUST 手写 SQL 显式 `AND deleted=0`；唯一索引列为空存 `null` 而非空串；逻辑删除同码新增先查含 deleted 记录再 restore。
- MUST 新增错误码前查重（Ehr/Portal 7001、Settings/File 8101+、Job/Openapi 9001+ 已有碰撞）。

## 配置与审计

- MUST Nacos yaml 纯 ASCII、`optional:` 前缀保留、List 型配置整体覆盖；MUST NOT 用下标覆盖网关路由。
- MUST 审计上报异步且失败只 warn；脱敏规则在 common `AuditDataMasker` 与 audit-client 两份，改一处 MUST 同步另一处。
- MUST audit 服务自身关闭日志采集（`polaris.system-log.enabled=false`）防递归。

## 交付验证

- MUST 通过 `mvn -o -q -pl <模块> -am -DskipTests compile` 后再交付。
- MUST 在最终回复列明需重启的应用及未提交/未部署状态。
