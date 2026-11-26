package com.laimengchao.sso.finance.client.config;

import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * 财务域 Feign 客户端专用配置：所有请求自动带 X-Internal-Token，
 * 与 finance-service 的 InternalTokenInterceptor 约定一致。
 * <p>刻意不标 @Configuration——经 @FeignClient#configuration 引入，标了会升级为全局配置污染其他 client。
 */
public class FinanceFeignConfig {

    @Value("${finance.client.internal-token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    @Bean
    public RequestInterceptor financeInternalTokenInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header("X-Internal-Token", internalToken);
            }
        };
    }

    @Bean
    public Request.Options financeRequestOptions() {
        return new Request.Options(3_000, 5_000, true);
    }
}
