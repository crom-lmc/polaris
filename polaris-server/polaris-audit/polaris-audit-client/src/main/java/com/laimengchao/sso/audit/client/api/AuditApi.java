package com.laimengchao.sso.audit.client.api;

import com.laimengchao.sso.audit.client.config.AuditFeignConfig;
import com.laimengchao.sso.audit.client.dto.AuditEventRequest;
import com.laimengchao.sso.audit.client.dto.LoginStatsQuery;
import com.laimengchao.sso.audit.client.dto.LoginStatsResult;
import com.laimengchao.sso.audit.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 统一审计事件上报（audit-server 的 /internal/audit/**）。
 * <p>
 * 三个来源共用本契约：网关 WebClient 异步兜底全量 ACCESS（不经过 Feign，但路径与字段一致），
 * 账号中心写 LOGIN / SENSITIVE，EHR 写 SENSITIVE。以及账号中心概览取登录统计。
 * 两端都凭 X-Internal-Token 准入。
 * <p>
 * 上报失败必须只告警不阻断业务——审计写入失败不能让已经发生的业务操作回滚或报错。
 */
@FeignClient(
        name = "audit-audit",
        contextId = "audit-client-audit-api",
        url = "${audit.client.base-url:${AUDIT_SERVICE_URL:http://127.0.0.1:9012}}",
        configuration = AuditFeignConfig.class)
public interface AuditApi {

    /** 单条上报 */
    @PostMapping("/internal/audit/event")
    R<Void> report(@RequestBody AuditEventRequest request);

    /** 批量上报（网关异步攒批用，降低请求数） */
    @PostMapping("/internal/audit/events")
    R<Void> reportBatch(@RequestBody List<AuditEventRequest> requests);

    /** 登录统计（账号中心概览用） */
    @PostMapping("/internal/audit/login-stats")
    R<LoginStatsResult> loginStats(@RequestBody LoginStatsQuery query);
}
