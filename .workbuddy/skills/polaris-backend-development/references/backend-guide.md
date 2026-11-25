# Polaris 后端开发规范

> 适用范围：`polaris-server/` 全部 Maven 模块（29 个 pom，12 个可执行服务）。
> 本文是基于现有代码事实总结的**强制约定**，每条都能在仓库里找到证据。新代码必须与既有风格一致；发现本文与代码冲突时，以代码为准并回头修正本文。
> 开始前请先读 `../../../rules/backend-00-L1-redlines.md` 与 `backend-01-L2-conventions.md` 的硬约束。

---

## 1. 技术栈（版本已锁定，不新增依赖）

| 项 | 版本 / 选型 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.2.5 |
| Spring Cloud | 2023.0.1（**不要升 2024.x**） |
| Spring Cloud Alibaba | 2023.0.1.0 |
| MyBatis-Plus | 3.5.7；`mybatis-spring` 必须 3.0.3+ |
| MySQL | 5.7，按业务分库 |
| 缓存 | Redis |
| 配置 | Nacos（默认关闭，靠 namespace + 环境变量切环境） |
| 网关 | Spring Cloud Gateway（**WebFlux**），验签 + 身份头注入 + 路由 |
| 认证 | JJWT，RS256 非对称密钥 |
| 工具 | Lombok（`@Data` / `@Getter` / `@RequiredArgsConstructor`） |

**全局编译参数**：根 pom 开启 `-parameters`（MVC 参数绑定与 MyBatis 反射依赖它），并绑定 `spring-boot-maven-plugin:repackage`（漏了会打出「没有主清单属性」的瘦 jar）。不要改这两处。

**禁止引入**：新 ORM、新 JSON 库、新日志框架、新调度框架、新安全框架。需要能力先在 `polaris-common` 或已有 `*-client` 中找。

---

## 2. 模块划分与依赖方向

```text
polaris-server/                       # 根聚合 pom（groupId com.laimengchao.sso）
├── polaris-common/                   # R / PageResult / BizException / ErrorCode /
│                                     # GlobalExceptionHandler / BaseEntity /
│                                     # AccountContext / PermissionCodes / DataScope
├── polaris-sso/                      # 聚合：gateway / auth / account / sso-client
├── polaris-cms/  polaris-portal/     # 单子模块 *-service
├── polaris-ehr/  polaris-settings/   # *-client + *-service
├── polaris-job/  polaris-file/       # *-client + *-service
├── polaris-forum/ polaris-openapi/   # 单子模块 *-service
├── polaris-audit/                    # *-client + *-server
├── polaris-nacos/                    # 配置包（local/prod yaml）
└── deploy/sql/                       # 生产迁移脚本
```

**依赖方向铁律**：

- `server` → `common` + 若干 `*-client`；业务服务之间只经 `*-client` 的 Feign 接口调用。
- **`*-client` 刻意不依赖 `common`**，各自带一份轻量 `R` 壳和 DTO，保持零传递负担；client 通过 `AutoConfiguration.imports` 自动装配。
- `common` 为兼容 WebFlux 网关，`spring-webmvc`、`jakarta.servlet-api`、`spring-jdbc`、`jsoup`、`poi-ooxml`、`spring-security-core` 全是 **optional**。**往 common 加依赖时，非 optional 的 MVC 依赖会让网关启动失败**。
- 版本统一在根 pom `dependencyManagement` 管理，子模块不写版本号。

### 服务端口（来自各服务 application.yml）

| 服务 | 端口 | 服务 | 端口 |
|---|---|---|---|
| sso-gateway | 9080 | job-service | 9008 |
| sso-auth | 9001 | forum-service | 9009 |
| sso-account | 9002 | openapi-service | 9010 |
| cms-service | 9004 | file-service | 9011 |
| portal-service | 9005 | audit-server | 9012 |
| ehr-service | 9006 | settings-service | 9007 |

9003 空缺（`blog` 已删除并迁出平台，不要恢复）。**WebFlux 只有 sso-gateway**，其余 12 个服务是 Servlet；网关对 `polaris-job-client` 显式排除 `spring-boot-starter-web`。

---

## 3. 包结构与实体基类

### 3.1 两套包模板，按规模选择

**模板 A：`module/<业务域>/`（audit、settings、cms、ehr、forum 等多域服务）**

```text
com.laimengchao.sso.audit.module.audit
├── controller/  AuditAdminController / InternalAuditController
├── entity/      AuditEvent / AuditSetting / SystemLog
├── mapper/      AuditEventMapper
├── service/     AuditEventService
├── vo/          LoginEventVO
└── job/         RetentionCleanupJob
```
跨切面（config / security）放顶层包；settings 的跨模块补偿任务放顶层 `schedule/`，域内 `job/` 放域自己的。

**模板 B：扁平分层（account，唯一用 `dto/request` + `dto/response` 的模块）**

```text
com.laimengchao.sso.account
├── controller/     AccountController / InternalAccountController
├── domain/entity/  SsoAccount / SsoRole / SsoPermission ...
├── dto/request/    CreateAccountRequest / LoginRequest ...
├── dto/response/   AccountVO / LoginResult / PermissionVO ...
├── service/        *Service 接口
├── service/impl/   *ServiceImpl 实现
├── security/       Permit / PermissionAspect / ScopeCodeCacheImpl
├── constant/       PermissionConstants（权限码 + BUILTIN 清单）
└── integration/    外部服务适配
```
小域用扁平，多域用 `module/<域>`；**不要强行把 account 改成 module 结构，也不要往 audit 里塞 dto/request**。

### 3.2 `BaseEntity` 必须继承

`polaris-common/.../common/BaseEntity.java` 提供：

- 雪花 ID：`IdType.ASSIGN_ID`，并用 `@JsonSerialize(ToStringSerializer.class)` 转字符串（19 位数字前端会丢精度）。
- `createdAt` / `updatedAt` 自动填充。
- `@TableLogic private Integer deleted = 0` 逻辑删除。

手工赋 ID 时用 `IdUtils.nextId()`。**逻辑删除的唯一索引可能不含 deleted 列**（如 `uk_role_code`），同码记录删除后无法再建，必须先查含 deleted 的记录再 `restore` 复活。

---

## 4. Controller 规范

### 4.1 URL 前缀

| 前缀 | 用途 | 鉴权 |
|---|---|---|
| `/api/<域>/admin/**` | 管理端接口 | `@Permit` 权限码 |
| `/api/<域>/public/**` | 匿名公开接口 | 网关白名单放行 |
| `/api/<域>/sso/**` | 单点换票回调 | 匿名，靠 ticket/OAuth code + appSecret |
| `/internal/**` | 服务间调用 | `X-Internal-Token`，fail-closed |

**网关白名单和路由写在 jar 内**：`polaris-sso-gateway/src/main/resources/application.yml:28-97` 与 `config/GatewayProperties.java:27-99`。新增 `/api/xxx/public/**` 或新服务路由必须改网关代码/配置并**重打网关 jar**，Nacos 的 `sso-gateway.yaml` 只覆盖 internal-token。漏掉 `/api/ehr/sso/**` 之类的换票路径会导致「回调被判未登录」。

### 4.2 标准写法

```java
@RestController
@RequestMapping("/api/audit/admin")
@RequiredArgsConstructor
public class AuditAdminController {

    private final AuditEventService auditEventService;

    @GetMapping("/events")
    @Permit(value = PermissionCodes.AUDIT_EVENTS_LIST,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<PageResult<AuditEvent>> events(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(auditEventService.page(page, size));
    }
}
```

- 构造器注入（`private final` + `@RequiredArgsConstructor`），不用字段注入。
- **`@Permit` 在每个服务各自的 `security` 包定义**（audit/settings/account 各一份），不是 common 里的通用注解。切面 `PermissionAspect` 顺序：未登录 401 → appCode 校验 → superAdmin 旁路 → 权限码 ANY/ALL 匹配 → 403。
- 身份从网关注入的可信头读取（`SsoHeaderAuthFilter` 翻译成 `SecurityUser` + `AccountContext`，**不验签不查库**，安全前提是端口仅内网可达）。当前操作人用 `AccountContext.getUsername()` / `getAccountId()`。
- 分页参数固定 `page`（默认 1）/ `size`（默认 10 或 20），返回 `R<PageResult<T>>`。
- **启动类 `scanBasePackages` 必须包含 `com.laimengchao.sso.common`**，否则 `GlobalExceptionHandler` 不生效。

### 4.3 HTTP 状态码语义（前端依赖）

401 / 403 / 404 / 409 必须同步 HTTP 状态（前端 axios 靠 401 触发重新授权），其余业务异常走 HTTP 200 + `code`。**换票失败用 `ErrorCode.BIZ_ERROR` 而不是 401**，否则前端会陷入重新授权死循环。

---

## 5. Service / Mapper 规范（与常见模板不同，务必注意）

> **事实：全仓没有 `extends ServiceImpl<...>` 或 `IService<...>` 用法。** 不要按网上 MyBatis-Plus 教程写 `IService`/`ServiceImpl`，本项目不用这一层。

实际模式：

```java
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SsoRoleMapper mapper;          // 直接注入 BaseMapper

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(RoleSaveRequest req) {
        Asserts.notEmpty(req.getCode(), "角色编码不能为空");
        SsoRole exist = mapper.selectByCodeIncludingDeleted(req.getCode());
        if (exist != null && exist.getDeleted() == 1) {
            restore(exist);                       // 唯一索引不含 deleted，需复活
            return;
        }
        SsoRole role = new SsoRole();
        role.setId(IdUtils.nextId());             // 手工赋雪花 ID
        // ... 字段填充
        mapper.insert(role);
    }

    public PageResult<SsoRole> page(int page, int size, String keyword) {
        LambdaQueryWrapper<SsoRole> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) w.like(SsoRole::getName, keyword.trim());
        w.orderByDesc(SsoRole::getCreatedAt);
        IPage<SsoRole> p = mapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }
}
```

- Mapper：`extends BaseMapper<T>`，自定义 SQL 用 `@Select` / `@Update` 注解或 XML（forum 有 18 个 `resources/mapper/**/*.xml`）。
- 查询一律 `LambdaQueryWrapper` / `LambdaUpdateWrapper` + `mapper.selectPage(selectList...)`；导出与分页共用同一个 wrapper 构造方法，避免筛选口径分叉。
- 事务：`@Transactional(rollbackFor = Exception.class)` 写在写方法上（全仓 63 处）。
- 参数校验两套风格并存：**Bean Validation**（`@Valid @RequestBody` + `@NotBlank/@Size`，cms/ehr 常用）与 **手工 `Asserts`**（`Asserts.notEmpty/isTrue/notNull`，account/audit/settings 常用，DTO 是裸 POJO）。按所在模块既有风格选，不要混搭。
- 幂等：先查再决定复用/复活；审批回调用 `instanceId` + 状态校验忽略重复投递；回调方返回 200 表示已收，5xx 才触发 settings 重试。
- 异常范式（关键链路不吞）：

```java
try { ... }
catch (BizException e) { throw e; }                       // 自己的业务异常原样抛
catch (FeignException e) {
    log.error("换取令牌失败", e);                          // 外部异常记完整堆栈
    throw new BizException(ErrorCode.SYSTEM_ERROR, "认证服务暂不可用，请稍后再试");
}
```

- **审计上报失败只告警不抛错**（`AuditApi` / `AuditLogAspect` / `AuditLogService` 都是 warn），绝不能让审计失败回滚业务。

---

## 6. 统一响应与错误码

| 类 | 路径 | 作用 |
|---|---|---|
| `R<T>` | `polaris-common/.../common/R.java` | 唯一响应壳 `{code,message,data,timestamp}`，`@JsonInclude(NON_NULL)` |
| `R.Page<T>` | 内部类 | `{list,total}` |
| `PageResult<T>` | 同包 | `{records,total,page,size}` |
| `BizException` | 同包 | 业务异常，三个构造：`IErrorCode` / `IErrorCode+detail` / `int+message` |
| `ErrorCode` | 同包 | 通用码 0/400/401/403/404/409/500/5000 |
| `IErrorCode` | 同包 | 契约：`getCode/getMessage/httpStatus()`，默认 0 走映射 |
| `GlobalExceptionHandler` | 同包 | `@RestControllerAdvice`，BizException 按码同步 HTTP 状态；校验异常→400；未知异常→500 + `log.error` |
| `Asserts` | `polaris-common/.../utils/Asserts.java` | `isTrue/isFalse/notNull/notEmpty`，不满足即抛 BizException |
| `AccountContext` | 同 utils 包 | ThreadLocal 身份与数据范围；过滤器 finally 必须 `clear()` |

- Controller 返回 `R.ok(data)` / `R.ok()`；失败抛 `BizException`，不要在 Controller 手动 `R.fail(...)` 后继续执行。
- 各服务在各自 `common/` 包定义 `*ErrorCode implements IErrorCode`：settings 8xxx（8001~8217），另有 cms / ehr / file / job / openapi / portal 各自的码段。
- **空数据返回空集合/空页，不返回 `null`**：`R` 的 `NON_NULL` 会省略 data 字段，前端解包拿到 `undefined`。
- 数据权限空范围直接 `PageResult.of(List.of(), 0, ...)`（fail-closed），禁止放行全量。

---

## 7. 权限体系（新增权限码必须三处齐）

权限码形如 `audit:events:export`，分菜单（`MENU(1)`）与按钮（`BUTTON(2)`）。

**"三处齐"落地为四个联动点，缺一不可**：

1. **common 常量**：`polaris-common/.../common/security/PermissionCodes.java`（供其他服务 `@Permit` 与前端 `meta.perm`）。头部注释明确：必须与 `sso_permission.code` 完全一致。
2. **account BUILTIN**：`polaris-sso-account/.../constant/PermissionConstants.java` 的对应 `*_BUILTIN` 列表（`PermDef(name, code, PermissionType, parentCode, sort)`）。`BootstrapInitializer.initPermissions()` 会在启动时按 app_code 幂等补齐缺失的 BUILTIN 码，**老库重启自动补齐**。
3. **全新库种子**：account 的 `src/main/resources/data.sql` 里 `INSERT IGNORE INTO sso_permission ...` 与角色授权 `sso_role_permission`；线上新增权限用 `deploy/sql/NN-xxx.sql`（幂等）并给对应角色授权。
4. **前端**：路由 `meta.perm` 与按钮显隐用同一字符串。

**内置权限码不可删除**：被 `@Permit` 直接引用，删掉接口永久 403 且界面无法恢复；只允许改名/改排序（`PermissionConstants.isBuiltin()` 与 `PermissionServiceImpl` 有运行期拦截）。

角色口径：管理员有写权限，查看员只读；新增导出/编辑权限不要授予查看员。数据权限在服务端 `DataScope` / `DataScopeHelper` / `ScopeCodeCache` 执行，业务侧 `applyOrgScope` 注入范围。

> 已知缺口（不要照抄）：CMS 的 applyOrgScope 存在 fail-open、薪资未脱敏；forum/job/settings/portal/openapi 未接入数据权限。

---

## 8. 审计接入

- Feign 契约：`polaris-audit-client` 的 `AuditApi`（`url=${audit.client.base-url:${AUDIT_SERVICE_URL:http://127.0.0.1:9012}}`），端点 `POST /internal/audit/event`、`/events`、`/login-stats`；接收端 `InternalAuditController` 调 `AuditEventService.record()`。
- DTO `AuditEventRequest` 的操作人规则：有主体 USER、内部调用 SYSTEM；`operatorName=SYSTEM` 不得因 id 空丢弃；**登录事件记尝试登录的用户名**（失败也记，用于撞库排查）。
- **自动留痕边界**：`AuditLogAspect` 默认拦截所有 `@RestController/@Controller` 的 POST/PUT/PATCH/DELETE（GET 由网关 ACCESS 兜底），可用 `@AuditLog(action/targetType/ignore)` 补充语义或跳过。
- **LOGIN / SENSITIVE 合规必留**：`AuditEventService.shouldDiscard()` 对这两类直接放行，不受采集开关与忽略规则影响。
- 账号中心显式敏感操作：`AuditLogService` 定义 `PASSWORD_RESET/CHANGE`、`ROLE_*`、`PERMISSION_*` 等，上报 `app=sso, eventCategory=SENSITIVE`。
- **audit 自身不自审计**：`audit application.yml` 设 `polaris.system-log.enabled=false`、`audit.log.enabled=false`，防止写日志再被采集形成循环。导出行为是例外，手工记一条 SENSITIVE。
- 匿名 `/public/**` 不在审计范围。
- 采集字段口径：网关只采 **query 串**（脱敏后写 `requestParams`），**不采 JSON body**；body 由业务 AOP 写 detail。`collect.queryParams` 在审计接收端生效；系统日志队列容量属于 common 的 `polaris.system-log.queue-size`。

---

## 9. 审批与横向能力

- 审批引擎在 **settings.approval**，业务单据（如 ehr.approval）经 `ApprovalApi` 对接；业务侧只做单据与回调，不重复实现流程引擎。
- 审批回调幂等：用 `instanceId` + 状态校验；settings 失败退避重试，**成功/失败必须用 HTTP 状态码区分**（settings 只认 2xx，真异常给 5xx 才重试，重复投递给 200）。`sla_hours` 在流程步骤上，实例用 snapshot 固化。
- 通知统一走 **settings.notice**（站内信 + SMTP）；forum.notification / ehr.announcement / openapi.outbound 都不是平台通知通道。
- 文件统一走 **file-client（FileApi `/internal/files/**`）+ file-service（:9011，库 polaris_file）**；PUBLIC 直链 / PRIVATE HMAC 签名；业务表只存 `file_id`，不自建附件表。
- 未实现能力（不要假设存在）：全局搜索、前端共享 npm 包、WebSocket/SSE、短信适配器、多租户、`@OperateLog` AOP。

---

## 10. 数据库与 SQL 脚本

### 10.1 两套 SQL 文件

1. **服务内 `schema.sql` / data.sql`**：`CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`（带完整列名）；`schema.sql` 明确不含 DROP/ALTER、不用于存量库升级。空种子脚本要留 `SELECT 1;`（Spring Boot 在 `mode=always` 时拒绝空脚本）。
2. **`deploy/sql/NN-描述.sql`**：`01-init.sql` … `13-audit-export.sql`，两位序号递增 + 小写连字符；`fix-/migration-/cleanup-` 表示定点变更。**新变更新增编号脚本，不修改历史脚本**。

### 10.2 字段约定

- 表前缀按域：`sso_` / `appr-`、`biz-`、`cfg-`（settings）/ `cms-` / `ehr-` / `job-` / `file_` / `api_`（openapi）/ `audit_`、`system_log` / `forum_`。
- snake_case 字段，`datetime NOT NULL` 时间，Java 端 `LocalDateTime`，输出 `yyyy-MM-dd HH:mm:ss`；MyBatis `map-underscore-to-camel-case: true`。
- 逻辑删除用 `deleted`。
- 存量库补列：参考 `AuditSchemaInitializer`（查 `information_schema`，缺列才 ALTER，失败只 warn）。

### 10.3 上线前检查

- [ ] **生产 `sql.init.mode=never`**（含 `AUDIT_SQL_INIT_MODE=never`）：新表必须手工执行 schema，不能指望启动自动建表；补列器只在表已存在时工作，空库 + never 会缺表。
- [ ] 新脚本幂等（IF NOT EXISTS / INSERT IGNORE / ON DUPLICATE KEY）。
- [ ] 删字段前全仓 grep：entity、`resources/mapper/**/*.xml`、schema.sql、data.sql、deploy/sql、DTO/VO、前端产物，以及 `target/classes` 副本（只改 source）。
- [ ] 涉及权限码的脚本同时播种与授权；执行前备份受影响表。
- [ ] MySQL 多表删除 `DELETE a FROM db.t a JOIN ...` 必须指定默认库：`mysql <db> < script.sql`，否则报 1046（即使写了全限定名）。

---

## 11. 配置、密钥与 Nacos

- **默认不启用 Nacos**：`spring.config.import: "optional:nacos:<dataId>.yaml?group=${SSO_NACOS_GROUP:SSO}&refreshEnabled=true"`，`SSO_NACOS_ENABLED=false` 为默认。没有 `application-{profile}.yml`，靠 namespace + 环境变量切环境。
- 配置优先级：**Nacos > jar 内 application.yml > 代码默认值**。配置包在 `polaris-nacos/config/{local,prod}/`，group 统一 `SSO`。
- Nacos 坑（务必遵守）：
  1. yaml 必须**纯 ASCII**：中文注释在 JDK17 + Windows GBK 下触发 `MalformedInputException`，而 `optional:` 会让它「服务起来了但配置没生效」。
  2. List 型配置（网关 routes）**整体覆盖不与 jar 合并**，加路由要把原有项都写上。
  3. 别用 `--spring.cloud.gateway.routes[0].uri=...` 下标覆盖（predicates 丢失）。
  4. namespace 填 **ID 不填名**；OpenAPI 查询/发布参数是 `tenant`。
  5. `optional:` 前缀不能省。
  6. 主密钥 `SSO_CONF_KEY` 不能随手换（历史密文全解不开、启动中止）；轮换要重加密全部 → 一次推送 → 同步密钥 → 重启。
- 敏感配置只用 `${ENV_VAR}` 或 `ENC(base64)` 注入（AES-256-GCM），**绝不入库、不写文档、不写日志**。
- 关键环境变量：`SSO_INTERNAL_TOKEN`（所有内部调用一致，缺失时 `/internal/**` 直接 503）、`SSO_JWT_PRIVATE_KEY` / `SSO_GATEWAY_PUBLIC_KEY`（必须配对）、各 `*_DB_*`、`*_SQL_INIT_MODE`、`*_APP_SECRET`。
- `target/`、`logs/`、上传目录、`.secrets/`、`.env` 不提交。

---

## 12. 编译、产物与部署

```bash
# 编译 / 打包（模块路径相对 polaris-server/）
/f/code/sso/.maven39/apache-maven-3.9.9/bin/mvn.cmd -o -q -pl polaris-audit/polaris-audit-server -am -DskipTests compile
/f/code/sso/.maven39/apache-maven-3.9.9/bin/mvn.cmd -o -q -pl polaris-audit/polaris-audit-server,polaris-sso/polaris-sso-account -am -DskipTests package
```

- **jar 命名**：9 个业务服务在 pom 里显式 `<finalName>`（audit-server、settings-service、cms-service 等）；sso-gateway / auth / account **没有 finalName**，产物是 `polaris-sso-gateway-1.0.0.jar` 等。部署时软链到 `deploy/jars/<短名>.jar`（如 `sso-account.jar`、`audit-server.jar`），不要拿 Maven 产物名直接找服务器文件。
- **`deploy/docker-compose.yml` 只有 9 个服务**（forum / sso-auth / sso-account / sso-gateway / settings / ehr / cms / portal / job），**audit / openapi / file 不在 compose 中**，需另部署。容器名 `polaris-<服务名>`，`network_mode: host`，健康检查用 TCP 探活。
- 部署仅在用户明确要求后执行：本地打包 → 上传 `/tmp/polaris-deploy-<时间戳>/` → 备份 jar 与受影响表到 `/opt/polaris/backups/pre-deploy-<时间戳>/` → 替换 `/opt/polaris/jars/<短名>.jar` → `docker restart` → 轮询 `docker inspect {{.State.Health.Status}}` 到 healthy → `curl --resolve <域名>:443:127.0.0.1` 自检。
- 最终回复必须列出**需重启的应用**；只改前端则明确说明无需重启。

---

## 13. 项目特有坑（违反即出错）

| 坑 | 正确做法 |
|---|---|
| 按教程写 `IService` / `ServiceImpl` | **全仓没有这种模式**；直接注入 `BaseMapper` + `LambdaQueryWrapper` |
| 新增权限码只改一处 | 四联动：common 常量 + account BUILTIN + data.sql/迁移 SQL + 前端 meta.perm/按钮 |
| 删除内置权限码 | 禁止删除（接口永久 403 且界面无法恢复），只能改名/改排序 |
| 新表依赖启动自动建表 | 生产 `sql.init.mode=never`，必须手工执行 schema |
| 改 common 依赖把 MVC 带进网关 | common 的 MVC/Servlet/JDBC 依赖必须 optional；网关禁引 `spring-boot-starter-web` |
| 网关路由只改 Nacos | 路由与白名单在 jar 内，Nacos List 配置整体覆盖；改完重打网关 jar |
| Nacos yaml 写中文注释 | 必须纯 ASCII，否则 GBK 环境解析失败且被 `optional:` 静默吞掉 |
| 混淆 HTTP 状态码 | 401/403/404/409 同步 HTTP 状态；换票失败用 BIZ_ERROR 避免前端授权死循环 |
| 审计上报失败抛异常 | 一律只 warn，不能回滚业务；audit 自身关闭日志采集防循环 |
| 把 forum.notification / ehr.announcement 当通知 | 平台通知统一走 settings.notice |
| 在网关采集 JSON body | 网关只采 query；body 由业务 AOP 写 detail |
| 审计队列容量在 audit 配置 | 实际在 common 的 `polaris.system-log.queue-size` |
| 导出行为做成可关闭配置 | 导出留痕强制开启，不暴露配置项 |
| 审批回调不幂等 | `instanceId` + 状态校验；2xx=已收，5xx 才重试，重复投递 200 |
| 逻辑删除后同码新增报重复 | 唯一索引不含 deleted，先查含 deleted 记录再 restore |
| 雪花 ID 前端丢精度 | `BaseEntity.id` 用 `@JsonSerialize(ToStringSerializer.class)` 转字符串 |
| 空数据范围返回全量 | 返回空集（fail-closed） |
| MySQL 多表 DELETE 不指定默认库 | 报 1046，需 `mysql <db> < script.sql` |
| 找错 jar 文件名 | 服务器用短名软链，与 Maven `finalName`/默认名不同 |
| 以为 compose 覆盖全部服务 | audit / openapi / file 不在 compose |
| push 被本地代理拦截 | 清除全部 proxy 环境变量后 push |
| 提交前不检查换行符 | 部分文件混合换行，Edit 会整文件归一化产生巨大假 diff；用 `git diff --ignore-space-at-eol` 核对后按原风格重放 |

---

## 14. 后端改动完成清单

- [ ] 分层正确、构造器注入、无新依赖；未使用 `IService`/`ServiceImpl`。
- [ ] `@Permit` 权限码四联动齐，SQL 已授权，内置码未删除。
- [ ] 写操作有审计留痕；审计失败只 warn；异常 `log.error` + 业务错误码。
- [ ] 回调幂等；数据范围 fail-closed；HTTP 状态码语义正确。
- [ ] SQL 脚本编号递增且幂等，删字段已全仓 grep。
- [ ] 新增配置项有真实引用点，不是空配置。
- [ ] Nacos 配置纯 ASCII、无下标覆盖、密钥未轮换。
- [ ] `mvn -o -q -pl <模块> -am -DskipTests compile` 通过。
- [ ] 迭代记录已写入仓库外 `F:\code\sso\tmp\repo\迭代文档\polaris-server\update.md`。
- [ ] 最终回复列出了需重启的应用与未部署/未提交状态。
