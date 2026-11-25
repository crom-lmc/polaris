# 后端公共代码实证规范（从代码提炼）

> 所有路径相对 `polaris-server/`。【共性】= 多服务一致；【例外】= 实现不一致；【坑】= 源码注释或历史变更证实的易错点。
> 本文件是代码事实的提炼，不是理想设计；与代码冲突时以代码为准。

## 1. common 底座

### 1.1 响应壳与异常（唯一口径）

- 【共性】`polaris-common/src/main/java/com/laimengchao/sso/common/R.java`：`{code,message,data,timestamp}`，`@JsonInclude(NON_NULL)`，`ok()/ok(T)/fail(...)`；**data 为 null 时字段被省略**，所以空数据必须返回空集合/空页，不能返回 null。
- 【例外】分页壳两套并存：`R.Page{list,total}`（cms/ehr 用）与 `PageResult{records,total,page,size}`（sso-account/audit/job/file 用）。**新增分页接口前先确认调用方前端期望哪套**，不要混用；注意 `Page.of(page,size)`（MyBatis-Plus 分页对象）与 `R.Page.of(list,total)`（响应壳）同名但语义不同。
- 【共性】`BizException` 三构造器；携带 `IErrorCode` 时由 `httpStatus()` 覆盖 HTTP 状态。`ErrorCode` 通用码：0/400/401/403/404/405/409/429/500/5000；各服务业务枚举实现 `IErrorCode`。
- 【坑】`GlobalExceptionHandler.java` 注释明确：401/403/404 **必须同步 HTTP 状态**（前端靠 401 触发重新授权，全返回 200 会让登录过期变成普通提示）；换票失败用 `BIZ_ERROR` 而不是 401，否则前端死循环。
- 【坑】`GlobalExceptionHandler` 生效前提是启动类 `scanBasePackages` 含 `com.laimengchao.sso.common`；**网关刻意不扫 common**（WebFlux 会注册两套异常体系，网关错误响应在 filter 内自己写）。

### 1.2 实体基类与身份上下文

- 【共性】`common/BaseEntity.java`：`@TableId(ASSIGN_ID)` + `@JsonSerialize(ToStringSerializer.class)` 的 Long id，`createdAt/updatedAt` 自动填充，`@TableLogic Integer deleted=0`。**19 位雪花 ID 必须转字符串**，否则前端 `JSON.parse` 丢精度、回查 404（`EntityMetaController.stringifyIds()`、`AccountController.appIds()` 均因此显式转换）。
- 【共性】`utils/AccountContext.java`：`ThreadLocal<AccountInfo>`，由 account 的 `LoginContextInterceptor` 与各服务 `SsoHeaderAuthFilter` 填充；**过滤器 finally 必须 `clear()`**，否则线程复用串号。
- 【共性】`security/SecurityUser.java` / `SecurityUtils.java` / `AuditContext.java`：身份统一来自账号中心；`AuditContext.current()` 取不到主体时返回 `Actor.system()`，IP 顺序 `X-Forwarded-For` → `X-Real-IP` → `remoteAddr`。
- 【共性】`security/JwtClaims.java` 定义载荷名与可信头名（`X-Account-Id/Name/Super/Perms/Apps`、`X-App-Code`、`X-Internal-Token`），签发了与验签端共用名字、不引 jjwt。
- 【坑】`JwtAuthFilter.stripTrustedHeaders()` 必须先剥离客户端自带的 `X-Account-*` 头再注入可信头；下游服务不验签不查库，安全前提是端口仅内网可达，直连可伪造超管身份。

### 1.3 工具与数据权限

- 【共性】`utils/Asserts.java`：`isTrue/isFalse/notNull/notEmpty`，不满足抛 `BizException`；**只接受 `ErrorCode`，不接受 `IErrorCode`**——业务码枚举需直接 `throw new BizException(XxxErrorCode...)`。
- 【共性】`utils/IdUtils.java`：内置雪花 `nextId()/nextIdStr()`（EPOCH=2024-01-01，synchronized，时钟回拨抛异常）；`JsonUtils` 统一 ObjectMapper；`ValidateUtil` 身份证/手机/邮箱校验（不收 15 位老身份证）。
- 【共性】安全类工具按需复用：`AuditDataMasker`（query/JSON/Bearer 脱敏，MAX_LENGTH=1800）、`SensitiveMasker`（手机前 3 后 4、身份证前 6 后 4）、`FileTypeValidator`（扩展名+魔数）、`HtmlSanitizer`（Jsoup Safelist 白名单）、`DownloadSecurityInterceptor`（非图片强制 attachment）。
- 【坑】`InMemoryRateLimiter` 只适合单实例；多实例各节点独立计数，需要分布式限流时不要直接复用。
- 【共性】数据权限三件套：`security/DataScope.java`（契约）、`DataScopeHelper.java`（**逗号分隔多值，任一前缀匹配放行，空返回 false**）、`ScopeCodeCache.java`（Redis 前缀 `sso:account:scope:`，TTL 10 分钟）。common 只定义契约，各模块自己实现；空范围必须返回空集 fail-closed。

## 2. 权限与鉴权（每服务一份，没有公共抽象）

- 【共性】`@Permit` + `PermissionAspect` 各有 9 份（audit/cms/ehr/file/forum/job/settings/sso-account，openapi 在 `security/admin`），`SsoHeaderAuthFilter` 有 9 份。改鉴权逻辑时要确认改的是哪个服务，不要以为改 common 一处全生效。
- 【共性】判定顺序固定：①未登录 401 → ②令牌 app 域不匹配 403 → ③超管旁路 → ④`ANY/ALL` 权限码匹配，失败 403 + `log.warn`。
- 【例外】app 域声明方式：audit 的 `Permit.app()` 为 `String[]` 多值任一；file/forum 为单值字符串；cms/ehr/job/settings/openapi **注解无 `app()` 成员**，由 Aspect 硬编码 `APP_CODE`。
- 【例外】`SsoHeaderAuthFilter` 两种形态：cms/ehr/forum/job/settings/portal 处理全路径；audit/file/openapi-admin 带 `@Order(0)` + `shouldNotFilter` 只处理 `/api/audit/admin/`、`/api/file/admin|my/`、`/api/open/admin/`。
- 【坑】ehr/settings 的 `Permit`/`PermissionAspect` 注释复制 cms 模板写"应用 code = cms"，**以 Aspect 里的 `APP_CODE` 为准**。
- 【坑】演示账号 `yanshi` 写操作由网关 `JwtAuthFilter.denyDemo()` 返回 HTTP 200 + 业务码 4031（避免前端当网络错误清令牌）；新增写接口需确认演示环境行为。
- 【坑】权限码四联动：common `PermissionCodes.java`（按 app_code 分组，注释要求"先改这里 → 再改 data.sql → 最后 DB 执行"）+ account `PermissionConstants` BUILTIN + `data.sql`/`deploy/sql` 播种授权 + 前端 `meta.perm`/按钮。实际播种主路径是 `BootstrapInitializer.initPermissions()`，两处都要同步。内置码不可删（运行期有拦截）。

## 3. `*-client` 模块规范

- 【共性】共 6 个 client（sso/audit/ehr/file/job/settings），各自 `META-INF/spring/...AutoConfiguration.imports` 注册一个 `@AutoConfiguration`，内部 `@EnableFeignClients(clients={...})` 精确注册；settings 额外提供 60s 缓存门面。
- 【共性】5 个 client 自带轻量 `dto/R.java`（`Integer code`、`String message`、`T data`、`@JsonIgnoreProperties(ignoreUnknown=true)`、`ok()`），**刻意不依赖 common**。
- 【例外】`polaris-job-client` 没有 `R.java`（它是 JobHandler 契约/执行器基础设施）；服务端 `common.R` 用 `int code` 且有 `timestamp`，改字段时两端 code/data 名要同步。
- 【共性】Feign 声明四要素（以 `AccountApi` 为准）：`name` + **独立 `contextId`** + 三级默认 url（配置键 → 环境变量 → `http://127.0.0.1:端口`）+ 专用 `configuration`；`@PathVariable("id")` 显式写名字。
- 【共性】Feign Config **不标 `@Configuration`**（否则变成全局配置），提供 `RequestInterceptor` 注入 `X-Internal-Token` 与 `Request.Options`；sso/ehr/audit 3s/10s，file 3s/30s（大附件）。
- 【坑】`AuthApi` OAuth token 端点必须 `application/x-www-form-urlencoded`，请求体用 `OAuthForm.encode()` 手工编码；改 `MultiValueMap` 会发空体或按 JSON 编码。
- 【坑】`ApprovalApi` 代理场景用 `*Raw` 返回 `R<JsonNode>`；若在代理层另声明 VO，漏 `@JsonSerialize(ToStringSerializer.class)` 会丢雪花 ID 精度。
- 【共性】`/internal/**` 由 common `InternalTokenInterceptor` + `CommonSecurityWebConfig` 统一校验：fail-closed（令牌未配置 503、头不匹配 401、`MessageDigest.isEqual` 常量时间比较）；客户端 Feign Config 注入同名头形成闭环。

## 4. 启动类与扫描

- 【共性】所有 Servlet 服务 `scanBasePackages` 含 `com.laimengchao.sso.common`；网关是唯一例外（见 §1.1）。
- 【坑】全仓**没有** `@EnableDiscoveryClient`（靠 `spring.cloud.nacos.discovery.enabled` 配置，默认 false）；**`@EnableFeignClients` 不在启动类**（由 client 自动装配，openapi 与 ehr 的 ForumClientConfig 是例外）。
- 【例外】Mapper 注册：audit/file/forum/job/openapi/settings/account 用启动类 `@MapperScan`；cms/ehr 用每个 Mapper 上的 `@Mapper`；portal 无 Mapper。
- 【坑】cms/ehr/portal 启动类 Javadoc 写"不扫 sso-common"，但代码实际包含 common；注释与代码矛盾时以代码为准。

## 5. Service / Mapper 真实写法

- 【共性】275 个文件用 `@RequiredArgsConstructor` + `private final` 构造器注入；Service 实现**本服务自定义的 `XxxService` 接口**，不是 MyBatis-Plus 的 `IService`。
- 【结论】全仓**未发现** `extends IService/ServiceImpl`；不要按网上教程写这一层。
- 【共性】Mapper `extends BaseMapper<T>`，复杂 SQL 用 `@Select` 或 `resources/mapper/**/*.xml`（18 个 XML），手写 SQL 用 `#{?}` 占位符。查询用 `LambdaQueryWrapper` + `mapper.selectPage/selectList`；导出与分页共用同一个 wrapper 构造方法，避免筛选口径分叉。
- 【共性】写方法 `@Transactional(rollbackFor = Exception.class)`；参数校验两套风格按模块既有风格选（cms/ehr 用 Bean Validation，account/audit/settings 用手工 `Asserts`），不要混搭。
- 【共性】预期业务失败用 `log.warn`（如登录失败），未知异常/任务失败用 `log.error`；外部异常带完整堆栈后转业务错误码；审计与日志上报失败只 warn，绝不回滚业务。
- 【共性】幂等：先查（含 deleted 记录）再决定复用/复活，或 SQL `INSERT IGNORE`；审批回调 `instanceId` + 状态校验，重复投递 200、真异常 5xx 才重试。
- 【坑】`LambdaQueryWrapper` 多个 `like...or()` 必须用 `w.nested(...)` 包起来，否则后续 `in()` 因 AND 优先级更高导致组织过滤只作用于最后一个 like（`AccountServiceImpl.java` 实证）。
- 【坑】唯一索引列为空时存 `null` 而不是 `""`（`uk_email` 多个空串会冲突）；逻辑删除唯一索引常不含 `deleted` 列，同码删除后要查含 deleted 的记录再 restore。
- 【坑】手写 SQL 必须显式 `AND deleted=0`（MyBatis-Plus 逻辑删除只作用于Wrapper）；cms 的 `id-type: auto`（自增）与 sso-account 的 `assign_id`（雪花）不同，跨服务迁移实体不可假设同一主键策略。

## 6. SQL 脚本与初始化器

- 【共性】10 个含库服务有 `schema.sql` + `data.sql`（`CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`）；空种子脚本留 `SELECT 1;` 防 Spring 空脚本报错。
- 【共性】`deploy/sql/` 编号 01–13 递增，全部幂等可重复执行；新变更新增编号脚本，不改历史脚本。
- 【例外】`spring.sql.init.mode`：sso-account 默认 `never`，其余服务多为 `always`；生产 Nacos 明确 `AUDIT_SQL_INIT_MODE=never`、`sso-account.yaml` never auto-DDL。
- 【坑】MySQL 5.7 **不支持 `ADD COLUMN IF NOT EXISTS`**，且 `CREATE TABLE IF NOT EXISTS` 对已存在表直接跳过——存量补列/换索引靠 `*SchemaInitializer`（查 `information_schema` 判断后执行，失败只 warn），新表仍须手工执行 schema。
- 【坑】`BootstrapInitializer` 启动跑 9 步（迁移、权限播种、角色、超管、内置应用）；内置 admin 口令缺失时跳过建号并告警，绝建空口令号。
- 【坑】码段碰撞：Ehr/Portal 都用 7001，Settings/File 都占 8101–8104，Job/Openapi 都占 9001+；各服务独立部署运行期无碍，全链路排查需注明服务名。新增错误码前先查重。

## 7. 审计与日志

- 【共性】`AuditLogAspect` 自动拦截所有 `@RestController` 的 POST/PUT/PATCH/DELETE，GET 由网关 `AuditReporter` 兜底；`@AuditLog(action/targetType/ignore)` 只用于补语义或跳过（目前仅 file 服务 6 个 Controller 显式标注）。
- 【共性】`AuditApi` 的 `AuditEventRequest`：有主体记 USER，无主体内部调用记 SYSTEM；**登录事件记尝试登录的用户名**（失败也记，用于撞库排查），不得退化 SYSTEM。
- 【坑】脱敏规则在 common `AuditDataMasker` 与 audit-client 内**两份独立维护**，改规则要同步两处。
- 【共性】system-log：`common/logging/SystemLogAutoConfiguration` 自动装配（AutoConfiguration.imports 注册），root logger 挂 `SystemLogAppender`，异步队列上报 `/internal/logs`。audit 自身必须 `polaris.system-log.enabled=false`，且 appender 按线程名 `"system-log-reporter"` 防递归。
- 【坑】`EncryptedPropertyEnvironmentPostProcessor` 发现 `ENC(...)` 但无 `SSO_CONF_KEY` 时直接启动失败，绝不把密文当明文用。

## 8. 定时任务

- 【共性】业务定时任务主流实现 `polaris-job-client` 的 `JobHandler`（`name()` + `execute(JobContext)`，由调度中心按 handler 名派发到 `/internal/jobs/run`），如 settings 的 `ApprovalSlaJob`、`ApprovalCallbackRetryJob`。补偿任务用退避 1/2/4/8/16/30 分钟。
- 【例外】`@Scheduled` 仅 4 处真实使用（audit 保留清理、file 分片清理/TTL、job 扫描），启动类需 `@EnableScheduling`；新业务定时任务优先走 JobHandler，不要默认本地 `@Scheduled`。
- 【共性】任务方法 `try-catch` + `log.error("...失败：{}", e.getMessage(), e)`，失败不让调度器静默吞掉。

## 9. 新增后端接口补充自查

- [ ] 响应壳、分页壳与本服务既有接口一致；空数据返回空集合/空页。
- [ ] 401/403/404 同步 HTTP 状态；业务异常抛 `BizException` 不在 Controller 手动 fail 后继续执行。
- [ ] `@Permit` app 域口径与本服务 Aspect 一致（不要照抄其他服务的注解写法）。
- [ ] 跨服务调用走 `*-client`，Feign 四要素齐、`contextId` 唯一、注入 internal token；新 client 用 `AutoConfiguration.imports` 注册。
- [ ] Wrapper 的 or/in 用 `nested()` 包裹；手写 SQL 带 `deleted=0`；唯一索引空值存 null。
- [ ] 写操作幂等；审计上报失败只 warn；预期失败 warn、未知异常 error。
- [ ] 新表手工执行 schema；存量补列用 SchemaInitializer 模式；错误码先查重。
