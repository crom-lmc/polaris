package com.laimengchao.sso.audit.module.audit.controller;

import com.laimengchao.sso.client.api.AuthApi;
import com.laimengchao.sso.client.exception.AuthOAuthException;
import com.laimengchao.sso.client.support.OAuthForm;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一审计中心单点登录换票：把 OAuth2 授权码换成 audit 作用域的用户令牌。
 * <p>
 * 与其他业务应用（file/settings/cms 等）的 SsoExchangeController 同构：
 * appSecret 只在本服务，前端只经手一次性授权码；端点匿名可访问
 * （网关白名单 /api/audit/sso/**）。
 */
@Slf4j
@RestController
@RequestMapping("/api/audit/sso")
@RequiredArgsConstructor
public class SsoExchangeController {

    private final AuthApi authApi;

    @Value("${sso.audit.app-code:audit}")
    private String appCode;

    @Value("${sso.audit.app-secret:}")
    private String appSecret;

    /** OAuth2 标准授权码换令牌：auth 返回裸 OAuth2 JSON，这里做 snake→camel 映射 */
    @PostMapping("/oauth/exchange")
    public R<Map<String, Object>> oauthExchange(@RequestBody Map<String, String> body) {
        String code = body == null ? null : body.get("code");
        String redirectUri = body == null ? null : body.get("redirectUri");
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "code 不能为空");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "redirectUri 不能为空");
        }
        requireSecret();
        try {
            Map<String, Object> resp = authApi.oauthToken(oauthForm(code, redirectUri));
            if (resp == null || resp.containsKey("error")) {
                String msg = resp == null ? "认证服务无响应"
                        : String.valueOf(resp.getOrDefault("error_description", resp.get("error")));
                throw new BizException(ErrorCode.BIZ_ERROR, msg);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            if (resp.containsKey("access_token")) out.put("accessToken", resp.get("access_token"));
            if (resp.containsKey("refresh_token")) out.put("refreshToken", resp.get("refresh_token"));
            if (resp.containsKey("token_type")) out.put("tokenType", resp.get("token_type"));
            if (resp.containsKey("expires_in")) out.put("expiresIn", resp.get("expires_in"));
            if (resp.containsKey("scope")) out.put("scope", resp.get("scope"));
            return R.ok(out);
        } catch (BizException e) {
            throw e;
        } catch (AuthOAuthException e) {
            throw new BizException(ErrorCode.BIZ_ERROR, e.displayMessage());
        } catch (FeignException e) {
            log.error("OAuth2 换码失败", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "认证服务暂不可用，请稍后再试");
        }
    }

    private String oauthForm(String code, String redirectUri) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("client_id", appCode);
        form.put("client_secret", appSecret);
        form.put("redirect_uri", redirectUri);
        return OAuthForm.encode(form);
    }

    private void requireSecret() {
        if (appSecret == null || appSecret.isBlank()) {
            log.error("未配置 sso.audit.app-secret，无法完成单点换票");
            throw new BizException(ErrorCode.SYSTEM_ERROR, "单点登录未配置应用密钥");
        }
    }
}
