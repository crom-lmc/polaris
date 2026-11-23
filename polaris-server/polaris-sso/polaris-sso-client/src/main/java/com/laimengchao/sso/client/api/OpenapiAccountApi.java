package com.laimengchao.sso.client.api;

import com.laimengchao.sso.client.config.SsoInternalFeignConfig;
import com.laimengchao.sso.client.dto.ApiAppInfo;
import com.laimengchao.sso.client.dto.ApiAppMutation;
import com.laimengchao.sso.client.dto.ApiAuthzGrantRequest;
import com.laimengchao.sso.client.dto.ApiAuthzRevokeRequest;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.client.dto.SignatureVerifyRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 接口开放平台 → 统一登录中心的专用 Feign 客户端。
 * <p>
 * 与 {@link AccountApi} 共用 {@link SsoInternalFeignConfig}（X-Internal-Token + 超时），
 * 单独拆一个 contextId 是为了不与既有客户端的配置上下文冲突。
 * 两个方法分别解决「第三方权限从哪来」和「签名密钥不扩散」：
 * <ul>
 *   <li>{@link #appPermissions(String)}：取应用注册的开放 API 权限码；</li>
 *   <li>{@link #verifySignature(SignatureVerifyRequest)}：由账号中心用 app_secret 重算 HMAC 验签。</li>
 * </ul>
 */
@FeignClient(
        name = "sso-account",
        contextId = "sso-account-openapi-client",
        url = "${sso.client.account-base-url:${SSO_ACCOUNT_URL:http://127.0.0.1:9002}}",
        configuration = SsoInternalFeignConfig.class)
public interface OpenapiAccountApi {

    @GetMapping("/internal/openapi/app-permissions")
    R<List<String>> appPermissions(@RequestParam("appCode") String appCode);

    @PostMapping("/internal/openapi/verify-signature")
    R<Boolean> verifySignature(@RequestBody SignatureVerifyRequest request);

    /** 给第三方应用授予开放接口权限码（幂等），返回新建数量 */
    @PostMapping("/internal/openapi/authz/grant")
    R<java.util.Map<String, Object>> grantAuthz(@RequestBody ApiAuthzGrantRequest request);

    /** 撤销第三方应用的开放接口权限码，返回删除数量 */
    @PostMapping("/internal/openapi/authz/revoke")
    R<java.util.Map<String, Object>> revokeAuthz(@RequestBody ApiAuthzRevokeRequest request);

    /** 第三方对接应用清单（含停用），供开放平台自助维护页展示 */
    @GetMapping("/internal/openapi/apps")
    R<List<ApiAppInfo>> listClientApps();

    /** 新增第三方对接应用，返回明文密钥（仅此一次） */
    @PostMapping("/internal/openapi/apps")
    R<ApiAppInfo> createClientApp(@RequestBody ApiAppMutation request);

    /** 编辑第三方对接应用（编码不可改） */
    @PostMapping("/internal/openapi/apps/update")
    R<ApiAppInfo> updateClientApp(@RequestBody ApiAppMutation request);

    /** 启停第三方对接应用 */
    @PostMapping("/internal/openapi/apps/toggle")
    R<Void> toggleClientApp(@RequestParam("appCode") String appCode,
                           @RequestParam("status") Integer status);

    /** 重置第三方对接应用密钥，返回新的明文密钥 */
    @PostMapping("/internal/openapi/apps/reset-secret")
    R<String> resetClientAppSecret(@RequestParam("appCode") String appCode);

    /** 删除第三方对接应用（含清理授权与权限码） */
    @PostMapping("/internal/openapi/apps/delete")
    R<Void> deleteClientApp(@RequestParam("appCode") String appCode);
}
