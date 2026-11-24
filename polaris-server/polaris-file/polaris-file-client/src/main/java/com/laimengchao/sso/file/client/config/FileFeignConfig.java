package com.laimengchao.sso.file.client.config;

import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * {@link com.laimengchao.sso.file.client.api.FileApi} 专用 Feign 配置
 * （经 @FeignClient#configuration 引入，不是全局配置，故不标 @Configuration）。
 * <p>
 * 1. 所有请求自动带 X-Internal-Token——与 file-service 的 InternalTokenInterceptor 约定一致；
 * 2. 连接 3s、读 30s：上传/下载走 base64 内联传输，比普通查询耗时，读超时放宽避免大附件误判。
 */
public class FileFeignConfig {

    @Value("${file.client.internal-token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    @Bean
    public RequestInterceptor fileInternalTokenInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header("X-Internal-Token", internalToken);
            }
        };
    }

    @Bean
    public Request.Options fileRequestOptions() {
        return new Request.Options(3_000, 30_000, true);
    }
}
