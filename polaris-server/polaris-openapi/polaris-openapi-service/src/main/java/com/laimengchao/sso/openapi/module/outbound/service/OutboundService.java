package com.laimengchao.sso.openapi.module.outbound.service;

import com.laimengchao.sso.openapi.security.dto.CallerIdentity;
import java.util.Map;

public interface OutboundService {

    /** 出站调用上下文。 */
    public record SendContext(String apiCode, String channel, CallerIdentity identity,
                              Map<String, Object> payload, String clientIp, String traceId) {
    }

    /** 出站统一入口。 */
    Object send(SendContext ctx);
}
