package com.laimengchao.sso.openapi.module.inbound.service;

import com.laimengchao.sso.openapi.security.dto.CallerIdentity;
import java.util.Map;

public interface InboundService {

    /** 调用上下文。 */
    public record InvokeContext(String apiCode, CallerIdentity identity, Map<String, String> pathVars,
                                Object body, String method, String requestPath, String clientIp,
                                String traceId, Map<String, Object> requestParams) {
    }

    /** 执行一个开放 API 调用，返回下游 data。 */
    Object invoke(InvokeContext ctx);
}
