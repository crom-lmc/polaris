# 跨服务接口提供规范（对外暴露能力）

> 当某个服务需要向其他服务提供接口时，按本文件执行。全部条目从现有代码实证，路径相对 `polaris-server/`。
> 核心原则：**接口契约收敛到 `*-client` 模块，调用方依赖 client，不经网关，直连服务端口。**

## 1. 现状（实证）

现有 6 个 client 模块：`audit / ehr / file / job / settings / sso`（均在各自服务目录下，如 `polaris-settings/polaris-settings-client`）。父 `pom.xml` 的 `dependencyManagement` 统一登记。

【共性】5 个是标准 Feign client（自带 `dto/R.java` 响应壳，**刻意不依赖 common**，零传递负担）；`job-client` 是例外——应用侧 SDK，无 `@FeignClient`、无 `R`，用 `RestClient` 上报，中心派发走 `/internal/jobs/run` 且额外校验 `X-Job-Token`。

【例外】openapi 服务自建了 `SettingsInternalApi` / `EhrInternalApi` / `AuthInternalApi` 未复用现成 client；ehr 服务内置 `ForumMemberApi` 未抽模块；settings 审批回调用裸 `RestTemplate`（避免循环依赖）。这些是历史决策，新增能力不要照抄。

## 2. 决策：新建还是复用

| 场景 | 做法 |
|---|---|
| 某个服务需要**向其他服务提供新能力** | 建 `polaris-<域>-client` 模块，或在该域已有 client 的 `api/` 下加接口类 |
| 同一服务域新增接口 | **复用**该域已有 client，加一个 Api 并注册到 `@EnableFeignClients(clients={...})`。实证：settings-client 一个域 12 个 Api 共享 base-url、靠不同 contextId 隔离；sso-client 的 AccountApi 与 OpenapiAccountApi 共用配置 |
| 新服务要调已有能力 | 直接在调用方 pom 加现有 client 依赖，构造器注入 Api 或门面 |
| 能力归属迁移 | 同步迁移 client 契约，在 AutoConfiguration 注释说明（如审计上报从账号域迁至 audit-client），不让新旧契约并存 |

## 3. 新建 client 的标准步骤（缺一不可）

1. **建模块** `polaris-<域>-client`，pom 依赖 `spring-cloud-starter-openfeign` + `spring-boot-autoconfigure` + `jackson-databind` + `slf4j-api` + `lombok(optional)`；**不依赖 polaris-common**；在根 `pom.xml` 的 `dependencyManagement` 登记。
2. **响应壳** `dto/R.java`：`@Data @JsonIgnoreProperties(ignoreUnknown=true)`，`Integer code`（0 成功）、`message`、`T data`、`ok()`。
3. **Feign 配置** `config/XxxFeignConfig.java`：
   - 不标 `@Configuration`（经 `@FeignClient#configuration` 引入；标了会升级为全局配置污染其他 client）。
   - `@Value("${<域>.client.internal-token:${SSO_INTERNAL_TOKEN:}}")` 取令牌。
   - `@Bean RequestInterceptor` 注入 `template.header("X-Internal-Token", token)`。
   - `@Bean Request.Options` 显式设超时。现有档位：读 3s/5s、写 3s/20s、审计旁路 3s/10s、文件 base64 3s/30s；**不要依赖 Feign 默认 60s**。
4. **接口契约** `api/XxxApi.java`：
   ```java
   @FeignClient(name = "<service>", contextId = "<域>-<能力>-client",
                url = "${<域>.client.base-url:${ENV_URL:http://127.0.0.1:<port>}}",
                configuration = XxxFeignConfig.class)
   public interface XxxApi {
       @GetMapping("/internal/xxx/{id}")
       R<XxxDTO> get(@PathVariable("id") Long id);
   }
   ```
   `name` 同服务可复用，`contextId` 必须唯一；url 用「域专属键 → 环境变量 → 本地默认」三级兜底（settings-client 是两级历史例外，新 client 用三级）。
5. **自动装配** `XxxClientAutoConfiguration.java`：`@AutoConfiguration` + `@EnableFeignClients(clients={...})`；需要门面时用 `@Bean @ConditionalOnMissingBean` 提供（如 `SettingsClient` 在 Feign 之上加本地缓存 + 故障兜底）。
6. **注册** `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 写全限定类名。

## 4. 服务端暴露与鉴权

- 建 `Internal*Controller`，`@RequestMapping("/internal/<域>")`，返回 `R.ok(...)`；方法语义与 client 的 Api 一一对应（例：`InternalFileController` 的 8 个方法与 `FileApi` 一一对应）。
- 服务端 `SecurityConfig` 把 `/internal/**` 放 `permitAll`，鉴权统一交给 common 的 `InternalTokenInterceptor`（`CommonSecurityWebConfig` 按 addPathPatterns 注册）：
  - 令牌未配置 → **503**（`ErrorCode.SYSTEM_ERROR`），令牌不匹配 → **401**（`ErrorCode.UNAUTHORIZED`），fail-closed。
  - 用 `MessageDigest.isEqual` 常量时间比较，防时序攻击。
  - 服务启动类 `scanBasePackages` 必须含 `com.laimengchao.sso.common`，否则拦截器不生效（网关是 WebFlux，是唯一例外）。
- 网关 `JwtAuthFilter.stripTrustedHeaders()` 会剥离客户端自带的 `X-Internal-Token` / `X-Account-*`；外部请求访问 `/api/auth/internal/**` 直接 403。
- 调用方显式判空解包：`resp != null && resp.ok() && resp.getData() != null ? resp.getData() : List.of()`。

## 5. 精度与契约坑

- **雪花 ID**：服务端 `BaseEntity` 用 `@JsonSerialize(ToStringSerializer.class)` 转字符串（有 `2116193763304861697` 被 JS 抹成 `...700` 的事故注释）；client 的 DTO 与请求体 MUST 用 `String` 承载 ID，不能用 Long/number。
- **Raw VO**：需要原样透传服务器 JSON（含字符串化 ID）时用 `R<JsonNode>` 的 `*Raw` 方法（settings `ApprovalApi` 有 15+ 个），由代理层解包后原样返回；不要在 client 复制服务器 VO 后漏写序列化注解。
- **两套 R 壳**：client 自带 `dto/R.java` 与 common 的 `R` 并存，解包逻辑不可混用（openapi 自建 client 用 common.R，settings-client 用自带 R）。
- **裸 JSON 例外**：`AuthApi.oauthToken()` 返回裸 `Map`（非 R），错误由 `AuthFeignConfig` 的 `ErrorDecoder` 解析；换票端点公开、不注入 internal token，靠 appCode+appSecret 证明身份。
- **token 缺失**：客户端拦截器在 token 为空时空转、请求照常发出但无令牌；服务端 401/503。读接口兜底空集合，关键链路（审批是否在途、演示模式）fail-closed 抛异常。
