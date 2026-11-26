package com.laimengchao.sso.ehr.module.audit.service;

public interface AuditLogService {

    /** 取不到登录主体（内部任务）时不记，避免产生无主审计。 */
    void record(String action, String targetType, Long targetId, String fields);
}
