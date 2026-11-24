package com.laimengchao.sso.audit.module.audit.controller;

import com.laimengchao.sso.audit.client.dto.AuditEventRequest;
import com.laimengchao.sso.audit.client.dto.LoginStatsQuery;
import com.laimengchao.sso.audit.client.dto.LoginStatsResult;
import com.laimengchao.sso.audit.module.audit.service.AuditEventService;
import com.laimengchao.sso.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务间审计接口（/internal/audit/**，不经网关，X-Internal-Token 准入）。
 * <p>
 * 两个来源：业务服务经 audit-client 的 AuditApi 补语义；网关 WebClient 异步兜底全量访问。
 * 上报是「尽力而为」——接收端自身任何异常都不应回传给调用方造成业务失败。
 */
@RestController
@RequestMapping("/internal/audit")
@RequiredArgsConstructor
public class InternalAuditController {

    private final AuditEventService auditEventService;

    @PostMapping("/event")
    public R<Void> report(@RequestBody AuditEventRequest request) {
        auditEventService.record(request);
        return R.ok();
    }

    @PostMapping("/events")
    public R<Void> reportBatch(@RequestBody List<AuditEventRequest> requests) {
        auditEventService.recordBatch(requests);
        return R.ok();
    }

    /** 账号中心概览：登录统计走审计库，原 sso_login_log 已下线。 */
    @PostMapping("/login-stats")
    public R<LoginStatsResult> loginStats(@RequestBody LoginStatsQuery query) {
        return R.ok(auditEventService.loginStats(query));
    }
}
