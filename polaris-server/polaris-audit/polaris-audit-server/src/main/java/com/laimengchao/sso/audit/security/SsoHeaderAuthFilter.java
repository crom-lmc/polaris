package com.laimengchao.sso.audit.security;

import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.utils.AccountContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

/**
 * 管理端：把 sso-gateway 验签后注入的可信头翻译成登录态。
 * 与 file / openapi / settings 的同名过滤器同构——不验签、不查库，权限随令牌下发。
 */
@Slf4j
@Component
@Order(0)
public class SsoHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/audit/admin/");
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
        String username = nvl(request.getHeader(JwtClaims.Headers.USERNAME));
        boolean superAdmin = "true".equalsIgnoreCase(request.getHeader(JwtClaims.Headers.SUPER_ADMIN));
        String appCode = nvl(request.getHeader(JwtClaims.Headers.APP_CODE));
        Set<String> permissions = split(request.getHeader(JwtClaims.Headers.PERMISSIONS));

        SecurityUser principal = SecurityUser.of(accountId, username, superAdmin, appCode, permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        AccountContext.set(new AccountContext.AccountInfo(
                accountId, username, permissions, superAdmin, appCode));
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
