package com.laimengchao.sso.account.service.impl;

import com.laimengchao.sso.account.service.LoginLogService;

import com.laimengchao.sso.audit.client.api.AuditApi;
import com.laimengchao.sso.audit.client.dto.AuditEventRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 登录审计。成功与失败都上报 audit-server（app=sso，eventCategory=LOGIN），
 * 本地不再写 sso_login_log，登录日志查询与统计统一由审计中心提供。
 * 写入失败不应影响登录本身，因此 record 内部吞掉异常。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogServiceImpl implements LoginLogService {

    private final AuditApi auditApi;

    public void record(String username, Long accountId, String loginType, boolean success,
                       String failReason, String clientIp, String userAgent) {
        // 操作人填「尝试登录的用户名」（失败时用户可能不存在也照记，便于排查撞库），
        // 不用 SYSTEM——SYSTEM 只用于无登录主体的内部 Feign 调用。
        try {
            String type = loginType == null ? "password" : loginType;
            String result = success ? "success" : "fail";
            AuditEventRequest event = new AuditEventRequest();
            event.setApp("sso");
            event.setEventCategory("LOGIN");
            event.setAction(success ? "LOGIN_SUCCESS" : "LOGIN_FAIL");
            event.setHttpMethod("POST");
            event.setRequestPath("/api/auth/login");
            event.setOperatorType("USER");
            event.setOperatorId(accountId == null ? null : String.valueOf(accountId));
            event.setOperatorName(username);
            event.setTargetType("ACCOUNT");
            event.setTargetId(accountId == null ? username : String.valueOf(accountId));
            event.setDetail("result=" + result
                    + (success || failReason == null || failReason.isBlank() ? "" : ";reason=" + failReason)
                    + ";loginType=" + type);
            event.setClientIp(clientIp);
            event.setUserAgent(userAgent);
            event.setLoginType(type);
            event.setResult(result);
            event.setFailReason(success ? null : failReason);
            auditApi.report(event);
        } catch (Exception e) {
            log.warn("上报登录审计事件失败（已忽略，不影响登录）: {}", e.getMessage());
        }
    }
}
