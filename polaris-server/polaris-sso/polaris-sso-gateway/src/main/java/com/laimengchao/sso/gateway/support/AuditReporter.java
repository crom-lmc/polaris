package com.laimengchao.sso.gateway.support;

import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.utils.AuditDataMasker;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关统一审计上报（WebClient 异步，fire-and-forget）。
 * <p>
 * 原则（用户 2026-11-23 明确）：所有「登录后才能做的动作」全部审计，GET 也算。
 * 验签通过的每个请求都记一条 ACCESS，业务细则由业务服务经 AuditApi 补 SENSITIVE/BUSINESS。
 * 上报失败只告警不阻断业务——审计写入失败绝不能让已经放行的请求失败。
 * <p>
 * 操作人：网关只处理带令牌的请求，一律 USER + 令牌里的 sub/username；
 * SYSTEM 只用于无登录主体的内部 Feign 调用（由业务方经 AuditApi 上报），网关不产生 SYSTEM 事件。
 * 登录成功/失败由 sso-account 经 audit-client 上报，操作人填尝试登录的用户名，不经网关这条链。
 * <p>
 * 2026-11-24 补齐：上报时机从「验签后立即」改为「响应完成后」，回填真实 HTTP 状态码、
 * 业务结果（2xx=success）与耗时；入参采集 query + JSON body（脱敏），文件/表单流不采集。
 * 业务返回码 R.code 由业务层 AOP 回填，网关不读响应体，避免破坏 WebFlux 流。
 */
@Slf4j
@Component
public class AuditReporter {

    /** 审计总开关，出问题可一键关掉不影响业务 */
    private final boolean enabled;

    private final WebClient client;

    public AuditReporter(@Qualifier("plainWebClientBuilder") WebClient.Builder plain,
                         @Qualifier("lbWebClientBuilder") WebClient.Builder lb,
                         @Value("${sso.gateway.audit.enabled:true}") boolean enabled,
                         @Value("${sso.gateway.audit.audit-url:${AUDIT_SERVICE_URL:http://127.0.0.1:9012}}") String auditUrl,
                         @Value("${sso.gateway.audit.internal-token:${SSO_INTERNAL_TOKEN:}}") String internalToken) {
        this.enabled = enabled;
        boolean useLb = auditUrl != null && auditUrl.startsWith("lb://");
        this.client = (useLb ? lb : plain)
                .baseUrl(auditUrl)
                .defaultHeader(JwtClaims.Headers.INTERNAL_TOKEN, internalToken == null ? "" : internalToken)
                .build();
    }

    /**
     * 装饰下游 filter 链：请求正常/异常/取消结束后，拿真实 HTTP 状态码与耗时上报一条 ACCESS。
     * doFinally 覆盖 complete/error/cancel，保证已经放行的请求不会因为审计遗漏或失败。
     *
     * @param requestParams 已脱敏的入参（query）
     * @param startNanos    请求进入时的纳秒时间戳，用于算耗时
     * @return 装饰后的链，语义与原链一致（不改变响应）
     */
    public Mono<Void> reportAfterComplete(Mono<Void> chain, ServerWebExchange exchange, Claims claims,
                                          String path, HttpMethod method, String requestParams, long startNanos) {
        if (!enabled) {
            return chain;
        }
        return chain.doFinally(signal -> {
            try {
                Integer status = exchange.getResponse().getStatusCode() == null
                        ? null : exchange.getResponse().getStatusCode().value();
                long costMs = (System.nanoTime() - startNanos) / 1_000_000L;
                Map<String, Object> event = build(exchange, claims, path, method,
                        requestParams, status, costMs);
                send(event, path);
            } catch (Exception e) {
                log.warn("构造审计事件失败（已忽略）: path={} reason={}", path, e.getMessage());
            }
        });
    }

    /**
     * 鉴权失败补记（无有效令牌主体）。
     * 有 claims 用其 sub/username；否则操作人记客户端 IP（UNKNOWN:IP），一律 result=fail。
     * 失败原因只写固定提示，绝不写令牌/密钥本身。
     */
    public void reportDenied(ServerWebExchange exchange, Claims claims, String path, HttpMethod method,
                             int status, String reason, long startNanos) {
        if (!enabled) {
            return;
        }
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            String app = claims == null ? null : claims.get(JwtClaims.APP, String.class);
            event.put("app", app == null || app.isBlank() ? "sso" : app);
            event.put("eventCategory", "ACCESS");
            String m = method == null ? "GET" : method.name();
            event.put("action", m + " " + path);
            event.put("httpMethod", m);
            event.put("requestPath", path);
            event.put("operatorType", "USER");
            if (claims != null && claims.getSubject() != null && !claims.getSubject().isBlank()) {
                event.put("operatorId", claims.getSubject());
                event.put("operatorName", nvl(claims.get(JwtClaims.USERNAME, String.class)));
            } else {
                String ip = clientIp(exchange);
                event.put("operatorId", "");
                event.put("operatorName", "UNKNOWN:" + (ip.isEmpty() ? "-" : ip));
            }
            event.put("targetType", "");
            event.put("targetId", "");
            event.put("result", "fail");
            event.put("detail", "result=fail;deny=" + nvl(reason));
            event.put("requestParams", "");
            event.put("httpStatus", status);
            event.put("responseSummary", "HTTP " + status + " " + nvl(reason));
            event.put("costMs", (System.nanoTime() - startNanos) / 1_000_000L);
            event.put("clientIp", clientIp(exchange));
            String ua = exchange.getRequest().getHeaders().getFirst("User-Agent");
            event.put("userAgent", ua == null ? "" : ua);
            event.put("occurredAt", System.currentTimeMillis());
            send(event, path);
        } catch (Exception e) {
            log.warn("构造鉴权失败审计失败（已忽略）: path={} reason={}", path, e.getMessage());
        }
    }

    /** 兼容旧调用：验签后立即上报一条无响应信息的 ACCESS（部分分支仍可能用到）。 */
    public void report(ServerWebExchange exchange, Claims claims, String path, HttpMethod method,
                       String resultDetail) {
        if (!enabled) {
            return;
        }
        try {
            Map<String, Object> event = build(exchange, claims, path, method, null, null, null);
            if (resultDetail != null && !resultDetail.isBlank()) {
                event.put("detail", resultDetail);
            }
            send(event, path);
        } catch (Exception e) {
            log.warn("构造审计事件失败（已忽略）: path={} reason={}", path, e.getMessage());
        }
    }

    private void send(Map<String, Object> event, String path) {
        client.post()
                .uri("/internal/audit/event")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(event)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        unused -> { },
                        e -> log.warn("审计上报失败（已忽略，不影响业务）: path={} reason={}",
                                path, e.getMessage()));
    }

    private Map<String, Object> build(ServerWebExchange exchange, Claims claims, String path,
                                      HttpMethod method, String requestParams, Integer status, Long costMs) {
        Map<String, Object> event = new LinkedHashMap<>();
        String app = claims.get(JwtClaims.APP, String.class);
        event.put("app", app == null || app.isBlank() ? "sso" : app);
        event.put("eventCategory", "ACCESS");
        String m = method == null ? "GET" : method.name();
        event.put("action", m + " " + path);
        event.put("httpMethod", m);
        event.put("requestPath", path);
        event.put("operatorType", "USER");
        event.put("operatorId", claims.getSubject());
        event.put("operatorName", nvl(claims.get(JwtClaims.USERNAME, String.class)));
        event.put("targetType", "");
        event.put("targetId", "");
        // result 按真实 HTTP 状态判定；入站阶段无法拿业务码，交给业务层 AOP 补 SENSITIVE
        boolean success = status == null || (status >= 200 && status < 400);
        event.put("result", success ? "success" : "fail");
        event.put("detail", success ? "result=success" : "result=fail;http=" + status);
        event.put("requestParams", requestParams == null ? "" : requestParams);
        event.put("httpStatus", status);
        event.put("responseSummary", status == null ? "" : "HTTP " + status);
        event.put("costMs", costMs);
        event.put("clientIp", clientIp(exchange));
        String ua = exchange.getRequest().getHeaders().getFirst("User-Agent");
        event.put("userAgent", ua == null ? "" : ua);
        event.put("occurredAt", System.currentTimeMillis());
        return event;
    }

    /**
     * 采集并脱敏 query 入参（验签后调用，无读 body 副作用）。
     * JSON body 入参由业务层 AOP 采集——那里能拿到反序列化后的对象，比网关读字节流更准确，
     * 且避免在 WebFlux 中重复消费 body 破坏下游。
     */
    public String captureParams(ServerWebExchange exchange) {
        try {
            String query = exchange.getRequest().getURI().getRawQuery();
            return query == null || query.isBlank() ? "" : AuditDataMasker.mask(query);
        } catch (Exception e) {
            log.debug("采集审计入参失败（已忽略）: reason={}", e.getMessage());
            return "";
        }
    }

    private String clientIp(ServerWebExchange exchange) {
        var headers = exchange.getRequest().getHeaders();
        String xff = headers.getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) return first;
        }
        String real = headers.getFirst("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        var remote = exchange.getRequest().getRemoteAddress();
        return remote == null ? "" : remote.getAddress().getHostAddress();
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
