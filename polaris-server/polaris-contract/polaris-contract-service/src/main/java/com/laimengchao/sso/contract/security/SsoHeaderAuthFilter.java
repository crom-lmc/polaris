package com.laimengchao.sso.contract.security;

import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.utils.AccountContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 把 sso-gateway 验签后注入的可信头翻译成登录态；不验签、不查库。 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SsoHeaderAuthFilter extends OncePerRequestFilter {

    private final ScopeCodeCache scopeCodeCache;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/contract/") && !uri.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            buildAuthentication(request);
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            AccountContext.clear();
        }
    }

    private void buildAuthentication(HttpServletRequest request) {
        String rawId = request.getHeader(JwtClaims.Headers.ACCOUNT_ID);
        if (rawId == null || rawId.isBlank()) {
            return;
        }
        Long accountId;
        try {
            accountId = Long.valueOf(rawId.trim());
        } catch (NumberFormatException e) {
            log.warn("非法的账户标识头：{}", rawId);
            return;
        }
        boolean internal = request.getRequestURI().startsWith("/internal/");
        String username = nvl(request.getHeader(JwtClaims.Headers.USERNAME));
        boolean superAdmin = !internal && "true".equalsIgnoreCase(request.getHeader(JwtClaims.Headers.SUPER_ADMIN));
        String appCode = internal ? "" : nvl(request.getHeader(JwtClaims.Headers.APP_CODE));
        Set<String> permissions = internal ? Set.of() : split(request.getHeader(JwtClaims.Headers.PERMISSIONS));

        SecurityUser principal = SecurityUser.of(accountId, username, superAdmin, appCode, permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        AccountContext.AccountInfo info = new AccountContext.AccountInfo(
                accountId, username, permissions, superAdmin, appCode);
        if (internal) {
            // 服务间调用由 InternalTokenInterceptor 准入，数据范围按调用方透传的 X-Account-Id 解析
            info.setDataScopeOrgCode(scopeCodeCache.get(accountId));
        }
        AccountContext.set(info);
    }

    private Set<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }
}
