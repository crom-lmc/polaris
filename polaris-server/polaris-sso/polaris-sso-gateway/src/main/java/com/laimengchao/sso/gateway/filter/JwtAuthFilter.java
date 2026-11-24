package com.laimengchao.sso.gateway.filter;

import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.gateway.config.GatewayProperties;
import com.laimengchao.sso.gateway.support.AuditReporter;
import com.laimengchao.sso.gateway.support.JwtVerifier;
import com.laimengchao.sso.gateway.support.RevocationCache;
import com.laimengchao.sso.utils.JsonUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 统一鉴权过滤器。执行顺序：
 * <ol>
 *   <li><b>先剥离</b>客户端自带的 X-Account-* 头——这一步漏了，整套权限就是摆设</li>
 *   <li>拒绝外部访问内部接口前缀</li>
 *   <li>白名单放行（登录/刷新/登出）</li>
 *   <li>验签 + 校验类型 + 查吊销</li>
 *   <li>把身份写回可信头，交给下游</li>
 * </ol>
 * 下游服务因此不需要任何 JWT 依赖，只读头即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION = "Authorization";

    /**
     * 演示账户用户名。该账号拥有全部菜单查看权限（绑定超管角色），但所有写操作在网关层被拒绝，
     * 用于对外演示时「可看不可改」。这里按用户名识别，与是否具有超管身份无关。
     */
    private static final String DEMO_ACCOUNT_USERNAME = "yanshi";
    /** 演示账户只读专用业务码，避免与通用 403 混淆，前端按 message 提示即可 */
    private static final int DEMO_READONLY_CODE = 4031;
    private static final String DEMO_READONLY_MESSAGE = "演示账户不能进行操作";

    private final GatewayProperties props;
    private final JwtVerifier verifier;
    private final RevocationCache revocationCache;
    private final AuditReporter auditReporter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        String path = exchange.getRequest().getURI().getPath();

        // 1) 先清掉不可信的身份头。
        // 开放平台第三方路径（签名模式）以 X-App-Code 作为应用标识参与 HMAC 验签，必须保留调用方自带值；
        // 管理端 /api/open/admin/** 与其余业务路径绝不允许自带，仍按身份头剥离、由网关注入。
        boolean openapiThirdParty = path.startsWith("/api/open/")
                && !path.startsWith("/api/open/admin/");
        ServerWebExchange clean = stripTrustedHeaders(exchange, openapiThirdParty);

        // 2) 内部接口只允许服务间内网直连，外部一律 403（补记失败审计，便于发现探测）
        if (props.getInternalPrefixes().stream().anyMatch(path::startsWith)) {
            // 外部探测内部接口的防护拒绝，留痕便于发现扫描行为（客户端 IP 见审计事件）
            log.warn("拒绝外部访问内部接口：path={} method={}", path, clean.getRequest().getMethod());
            auditReporter.reportDenied(clean, null, path, clean.getRequest().getMethod(),
                    HttpStatus.FORBIDDEN.value(), "internal-forbidden", startNanos);
            return deny(clean, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "内部接口不对外开放");
        }

        // 3) 公开接口允许匿名；若携带有效令牌则继续注入身份，供详情返回点赞、收藏等个人状态
        boolean whitelisted = isWhitelisted(path);
        String token = bearer(clean.getRequest().getHeaders().getFirst(AUTHORIZATION));
        if (token == null || token.isBlank()) {
            if (whitelisted) return chain.filter(clean);
            auditReporter.reportDenied(clean, null, path, clean.getRequest().getMethod(),
                    HttpStatus.UNAUTHORIZED.value(), "no-token", startNanos);
            return deny(clean, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "未登录，请先获取访问令牌");
        }

        Claims claims;
        try {
            claims = verifier.verify(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 验签失败属安全相关拒绝，升级为 WARN（令牌伪造/过期/篡改的信号）
            log.warn("JWT 验签失败 path={} reason={}", path, e.getMessage());
            if (whitelisted) return chain.filter(clean);
            auditReporter.reportDenied(clean, null, path, clean.getRequest().getMethod(),
                    HttpStatus.UNAUTHORIZED.value(), "invalid-token", startNanos);
            return deny(clean, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "令牌无效或已过期");
        }

        if (!JwtClaims.TYPE_ACCESS.equals(claims.get(JwtClaims.TOKEN_TYPE, String.class))) {
            if (whitelisted) return chain.filter(clean);
            auditReporter.reportDenied(clean, claims, path, clean.getRequest().getMethod(),
                    HttpStatus.UNAUTHORIZED.value(), "wrong-token-type", startNanos);
            return deny(clean, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "该接口需要 access token");
        }
        if (revocationCache.isRevoked(claims.getId()) || isSessionRevoked(claims)) {
            if (whitelisted) return chain.filter(clean);
            auditReporter.reportDenied(clean, claims, path, clean.getRequest().getMethod(),
                    HttpStatus.UNAUTHORIZED.value(), "revoked", startNanos);
            return deny(clean, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }

        // 4.2) 统一审计兜底：验签通过、确认未吊销后，对这次请求记一条 ACCESS。
        // GET 也算动作（用户 2026-11-23 明确），因此这里不区分读写方法。
        // 上报延迟到响应完成后，回填真实状态码/结果/耗时；入参在此刻（下游读取前）采集。
        String requestParams = auditReporter.captureParams(clean);

        // 4.1) 演示账户只读：验签通过后，对非白名单的写操作一律拒绝。
        // 白名单路径（登录/刷新/登出/换票等）必须放行，否则演示账号根本登不进来、也拿不到令牌。
        // 用 HTTP 200 + 业务码返回，使响应进入前端统一的「code != 0 → message.error」分支，
        // 用户点击任何新增/编辑/删除按钮时都会看到「演示账户不能进行操作」。
        if (DEMO_ACCOUNT_USERNAME.equals(claims.get(JwtClaims.USERNAME, String.class))
                && !whitelisted && isWriteMethod(clean.getRequest().getMethod())) {
            auditReporter.reportDenied(clean, claims, path, clean.getRequest().getMethod(),
                    DEMO_READONLY_CODE, "demo-readonly", startNanos);
            return denyDemo(clean);
        }

        // 5) 注入可信头
        // X-App-Code 取令牌里的 app 声明，而不是让调用方自己填：
        // 它决定下游用哪一套权限码，客户端能改就等于能越权
        ServerHttpRequest enriched = clean.getRequest().mutate()
                .header(JwtClaims.Headers.ACCOUNT_ID, nvl(claims.getSubject()))
                .header(JwtClaims.Headers.USERNAME, nvl(claims.get(JwtClaims.USERNAME, String.class)))
                .header(JwtClaims.Headers.SUPER_ADMIN,
                        String.valueOf(Boolean.TRUE.equals(claims.get(JwtClaims.SUPER_ADMIN, Boolean.class))))
                .header(JwtClaims.Headers.APP_CODE, nvl(claims.get(JwtClaims.APP, String.class)))
                .header(JwtClaims.Headers.PERMISSIONS, nvl(claims.get(JwtClaims.PERMISSIONS, String.class)))
                .header(JwtClaims.Headers.APPS, nvl(claims.get(JwtClaims.APPS, String.class)))
                .build();
        Mono<Void> downstream = chain.filter(clean.mutate().request(enriched).build());
        String app = nvl(claims.get(JwtClaims.APP, String.class));
        String username = nvl(claims.get(JwtClaims.USERNAME, String.class));
        // INFO 挂在响应式链上，确保是「响应完成后」打印，而非 filter 装配期就打印
        return auditReporter.reportAfterComplete(downstream, clean, claims, path,
                        clean.getRequest().getMethod(), requestParams, startNanos)
                .doOnSuccess(v -> log.info("网关鉴权放行：path={} method={} app={} username={}",
                        path, clean.getRequest().getMethod(), app, username));
    }

    /**
     * 白名单判定：精确匹配，或以 {@code /**} 结尾时按前缀匹配。
     * 业务应用的读者端（如 /api/cms/public/**）必须能整段放行，
     * 否则每加一个公开接口都得回来改一次网关。
     */
    private boolean isWhitelisted(String path) {
        for (String pattern : props.getWhitelist()) {
            if (pattern.endsWith("/**")) {
                if (path.startsWith(pattern.substring(0, pattern.length() - 3))) {
                    return true;
                }
            } else if (pattern.equals(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 撕掉任何客户端自称的身份，杜绝伪造 X-Account-Id 冒充管理员。
     *
     * @param keepAppCode 开放平台第三方签名模式以 X-App-Code 标识调用方（签名材料的一部分），需原样透传；
     *                    该路径由开放平台服务自行验签鉴权，网关保留此头不等于相信其身份。
     */
    private ServerWebExchange stripTrustedHeaders(ServerWebExchange exchange, boolean keepAppCode) {
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(JwtClaims.Headers.ACCOUNT_ID);
                    h.remove(JwtClaims.Headers.USERNAME);
                    h.remove(JwtClaims.Headers.SUPER_ADMIN);
                    h.remove(JwtClaims.Headers.PERMISSIONS);
                    h.remove(JwtClaims.Headers.APPS);
                    if (!keepAppCode) {
                        h.remove(JwtClaims.Headers.APP_CODE);
                    }
                    h.remove(JwtClaims.Headers.INTERNAL_TOKEN);
                })
                .build();
        return exchange.mutate().request(sanitized).build();
    }

    /**
     * 会话级吊销：同一个浏览器里，别的应用换出来的令牌 jti 各不相同，但 sid 相同。
     * 只查 jti 的话，「A 应用退出、B 应用照常」——这正是本节要解决的问题。
     * 存量令牌没有 sid 声明，此处按未吊销处理，行为与改动前一致。
     */
    private boolean isSessionRevoked(Claims claims) {
        String sid = claims.get(JwtClaims.SESSION_ID, String.class);
        return sid != null && !sid.isBlank()
                && revocationCache.isRevoked(JwtClaims.SID_REVOKE_PREFIX + sid);
    }

    /**
     * 写方法判定：POST/PUT/PATCH/DELETE 视为写操作；GET/HEAD/OPTIONS 为只读。
     */
    private boolean isWriteMethod(HttpMethod method) {
        if (method == null) return false;
        return HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method)
                || HttpMethod.PATCH.equals(method) || HttpMethod.DELETE.equals(method);
    }

    /**
     * 演示账户只读拒绝：用 HTTP 200 + 业务码，让响应进入前端统一的业务失败分支，
     * 由各应用的 message.error(e.message) 展示「演示账户不能进行操作」。
     * 不用 403 是为了避免前端把 4xx 当成网络/鉴权错误而清令牌或跳登录页。
     */
    private Mono<Void> denyDemo(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        byte[] bytes = JsonUtils.toJson(R.fail(DEMO_READONLY_CODE, DEMO_READONLY_MESSAGE))
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> deny(ServerWebExchange exchange, HttpStatus status, ErrorCode code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        byte[] bytes = JsonUtils.toJson(R.fail(code, message)).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private String bearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String v = authorization.trim();
        return v.regionMatches(true, 0, "Bearer ", 0, 7) ? v.substring(7).trim() : v;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 必须早于路由过滤器执行 */
    @Override
    public int getOrder() {
        return -100;
    }
}
