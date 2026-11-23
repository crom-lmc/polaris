package com.laimengchao.sso.openapi.module.integration;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.openapi.config.InternalFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/** sso-auth 内部签发接口：第三方机器令牌由 sso-auth 用 RS256 私钥统一签发。 */
@FeignClient(
        name = "auth-internal",
        contextId = "openapi-auth-internal",
        url = "${sso.client.auth-base-url:${SSO_AUTH_URL:http://127.0.0.1:9001}}",
        configuration = InternalFeignConfig.class)
public interface AuthInternalApi {

    /** 请求体契约与 sso-auth ClientTokenRequest 对应（字段名一致，按 JSON 映射，避免跨模块依赖）。 */
    @PostMapping("/api/auth/internal/client-token")
    R<Map<String, Object>> issueClientToken(@RequestBody ClientTokenRequest request);

    /** 与 sso-auth ClientTokenRequest 同构的 DTO（放本模块，避免 openapi 依赖 auth 的 DTO 类）。 */
    class ClientTokenRequest {
        private Long appId;
        private String appCode;
        private String app;
        private List<String> permissions;

        public Long getAppId() { return appId; }
        public void setAppId(Long appId) { this.appId = appId; }
        public String getAppCode() { return appCode; }
        public void setAppCode(String appCode) { this.appCode = appCode; }
        public String getApp() { return app; }
        public void setApp(String app) { this.app = app; }
        public List<String> getPermissions() { return permissions; }
        public void setPermissions(List<String> permissions) { this.permissions = permissions; }
    }
}
