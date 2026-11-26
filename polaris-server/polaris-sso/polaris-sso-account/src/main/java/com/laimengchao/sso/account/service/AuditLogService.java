package com.laimengchao.sso.account.service;

public interface AuditLogService {

    void record(String action, String targetType, Long targetId, String fields);

    /** 失败审计尽力写入，不能用审计异常覆盖原业务异常。 */
    void recordFailure(String action, String targetType, Long targetId, String fields);

    /** 无登录主体的内部调用（如邮件重置密码），由调用方显式给出操作人与原始客户端信息。 */
    void recordInternal(Long operatorId, String operatorName, String action, String targetType, Long targetId, String fields, String clientIp, String userAgent);
}
