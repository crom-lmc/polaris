package com.laimengchao.sso.account.service;

import com.laimengchao.sso.audit.client.api.AuditApi;
import com.laimengchao.sso.audit.client.dto.AuditEventRequest;
import com.laimengchao.sso.common.security.AuditContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SSO 敏感操作审计。统一上报 audit-server 的 audit_event（app=sso，eventCategory=SENSITIVE），
 * 不再写本地 sso_audit_log 表；登录审计由 LoginLogService 单独上报。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String ROLE_CREATE = "ROLE_CREATE";
    public static final String ROLE_UPDATE = "ROLE_UPDATE";
    public static final String ROLE_DELETE = "ROLE_DELETE";
    public static final String ROLE_PERMISSION_ASSIGN = "ROLE_PERMISSION_ASSIGN";
    public static final String PERMISSION_CREATE = "PERMISSION_CREATE";
    public static final String PERMISSION_UPDATE = "PERMISSION_UPDATE";
    public static final String PERMISSION_DELETE = "PERMISSION_DELETE";
    public static final String PERMISSION_IMPORT = "PERMISSION_IMPORT";
    public static final String PERMISSION_DELETE_BY_APP = "PERMISSION_DELETE_BY_APP";

    public static final String TARGET_ACCOUNT = "ACCOUNT";
    public static final String TARGET_ROLE = "ROLE";
    public static final String TARGET_PERMISSION = "PERMISSION";
    public static final String TARGET_APPLICATION = "APPLICATION";

    private static final String APP = "sso";
    private static final String CATEGORY = "SENSITIVE";

    private final AuditApi auditApi;

    public void record(String action, String targetType, Long targetId, String fields) {
        publish(action, targetType, targetId, true, fields, null);
    }

    /** 失败审计尽力写入，不能用审计异常覆盖原业务异常。 */
    public void recordFailure(String action, String targetType, Long targetId, String fields) {
        try {
            publish(action, targetType, targetId, false, fields, null);
        } catch (Exception e) {
            log.warn("写入失败操作审计失败（已忽略）: {}", e.getMessage());
        }
    }

    /** 无登录主体的内部调用（如邮件重置密码），由调用方显式给出操作人与原始客户端信息。 */
    public void recordInternal(Long operatorId, String operatorName, String action,
                               String targetType, Long targetId, String fields,
                               String clientIp, String userAgent) {
        if (operatorId == null) {
            return;
        }
        AuditContext.Actor actor = new AuditContext.Actor(operatorId, operatorName, clientIp, userAgent);
        publish(action, targetType, targetId, true, fields, actor);
    }

    private void publish(String action, String targetType, Long targetId, boolean success,
                         String fields, AuditContext.Actor explicitActor) {
        AuditContext.Actor actor = explicitActor == null ? AuditContext.current() : explicitActor;
        // 取不到登录主体时不记，避免产生无主审计；内部调用请用 recordInternal 显式指定
        if (actor == null || actor.accountId() == null) {
            return;
        }
        String result = success ? "success" : "fail";
        try {
            AuditEventRequest req = new AuditEventRequest();
            req.setApp(APP);
            req.setEventCategory(CATEGORY);
            req.setAction(action);
            req.setOperatorType(explicitActor == null ? "USER" : "SYSTEM");
            req.setOperatorId(String.valueOf(actor.accountId()));
            req.setOperatorName(actor.username());
            req.setTargetType(targetType);
            req.setTargetId(targetId == null ? "" : String.valueOf(targetId));
            req.setDetail("result=" + result + (fields == null || fields.isBlank() ? "" : ";" + fields.trim()));
            req.setResult(result);
            req.setClientIp(actor.clientIp());
            req.setUserAgent(actor.userAgent());
            auditApi.report(req);
        } catch (Exception e) {
            log.warn("上报 SSO 敏感操作审计失败（已忽略）: action={} reason={}", action, e.getMessage());
        }
    }
}
