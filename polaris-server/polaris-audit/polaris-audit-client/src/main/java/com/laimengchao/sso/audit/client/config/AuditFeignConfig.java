package com.laimengchao.sso.audit.client.config;

import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * {@link com.laimengchao.sso.audit.client.api.AuditApi} 专用 Feign 配置
 * （经 @FeignClient#configuration 引入，不是全局配置，故不标 @Configuration）。
 * <p>
 * 1. 所有请求自动带 X-Internal-Token——与 audit-server 的 InternalTokenInterceptor 约定一致；
 * 2. 连接 3s、读 10s：审计上报是旁路链路，宁可快速失败也不能拖慢业务请求。
 */
public class AuditFeignConfig {

    @Value("${audit.client.internal-token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    @Bean
    public RequestInterceptor auditInternalTokenInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header("X-Internal-Token", internalToken);
            }
        };
    }

    @Bean
    public Request.Options auditRequestOptions() {
        return new Request.Options(3_000, 10_000, true);
    }
}
