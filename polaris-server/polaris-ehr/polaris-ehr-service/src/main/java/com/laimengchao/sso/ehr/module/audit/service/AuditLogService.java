package com.laimengchao.sso.ehr.module.audit.service;

import com.laimengchao.sso.audit.client.api.AuditApi;
import com.laimengchao.sso.audit.client.dto.AuditEventRequest;
import com.laimengchao.sso.common.security.AuditContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EHR 敏感操作审计：详情查看 / 明文查看 / 导出 / 导入都上报一条，
 * 统一写 audit-server 的 audit_event（app=ehr，eventCategory=SENSITIVE），不再写 ehr_audit_log 表。
 * 查询入口在统一审计中心，EHR 管理端不再保留审计日志菜单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final String ACTION_VIEW = "VIEW";
    public static final String ACTION_PLAIN = "PLAIN";
    public static final String ACTION_EXPORT = "EXPORT";
    public static final String ACTION_IMPORT = "IMPORT";

    public static final String TARGET_EMPLOYEE = "EMPLOYEE";
    public static final String TARGET_CONTRACT = "CONTRACT";
    public static final String TARGET_ATTENDANCE = "ATTENDANCE";
    public static final String TARGET_IMPORT = "IMPORT";

    private final AuditApi auditApi;

    /** 取不到登录主体（内部任务）时不记，避免产生无主审计。 */
    public void record(String action, String targetType, Long targetId, String fields) {
        AuditContext.Actor actor = AuditContext.current();
        if (actor.accountId() == null) {
            return;
        }
        try {
            AuditEventRequest req = new AuditEventRequest();
            req.setApp("ehr");
            req.setEventCategory("SENSITIVE");
            req.setAction(action);
            req.setOperatorType("USER");
            req.setOperatorId(String.valueOf(actor.accountId()));
            req.setOperatorName(actor.username());
            req.setTargetType(targetType);
            req.setTargetId(targetId == null ? "" : String.valueOf(targetId));
            req.setDetail("result=success" + (fields == null || fields.isBlank() ? "" : ";" + fields.trim()));
            req.setResult("success");
            req.setClientIp(actor.clientIp());
            req.setUserAgent(actor.userAgent());
            auditApi.report(req);
        } catch (Exception e) {
            log.warn("上报 EHR 敏感操作审计失败（已忽略）: action={} reason={}", action, e.getMessage());
        }
    }
}
