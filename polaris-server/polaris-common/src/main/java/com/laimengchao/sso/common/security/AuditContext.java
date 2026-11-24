package com.laimengchao.sso.common.security;

import com.laimengchao.sso.utils.AccountContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计事件的操作人/客户端上下文提取。各服务把结果交给 audit-client 上报，
 * 不再各自用 JdbcTemplate 写本地审计表。
 * <p>
 * 取不到登录主体时返回 {@link Actor#system()}，由调用方决定是否上报——
 * 登录审计例外：操作人必须填尝试登录的用户名，不能退化成 SYSTEM。
 */
public final class AuditContext {

    private AuditContext() {
    }

    public record Actor(Long accountId, String username, String clientIp, String userAgent) {

        /** 无登录主体的内部调用 */
        public static Actor system() {
            return new Actor(null, "SYSTEM", "", "");
        }

        public boolean anonymous() {
            return accountId == null;
        }
    }

    public static Actor current() {
        Long accountId = AccountContext.getAccountId();
        String username = AccountContext.getUsername();
        if (accountId == null) {
            SecurityUser user = SecurityUtils.currentUser().orElse(null);
            if (user != null) {
                accountId = user.getAccountId();
                username = user.getUsername();
            }
        }
        HttpServletRequest request = currentRequest();
        String ip = request == null ? "" : clientIp(request);
        String ua = request == null ? "" : nvl(request.getHeader("User-Agent"));
        return new Actor(accountId, nvl(username), ip, ua);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return nvl(request.getRemoteAddr());
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
