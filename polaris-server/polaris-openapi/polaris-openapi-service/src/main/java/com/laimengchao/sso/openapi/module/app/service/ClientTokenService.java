package com.laimengchao.sso.openapi.module.app.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.app.entity.ApiClientApp;
import com.laimengchao.sso.openapi.module.integration.AuthInternalApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 第三方应用换票编排（对外 auth 能力）。
 * <p>
 * 流程：本地库校验 appCode+appSecret → 取该应用被授权的权限码 →
 * 调 sso-auth 内部端点用 RS256 私钥签发机器令牌（sub=应用数字 ID）。
 * 不发 refresh——过期后第三方凭凭证重新换票。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientTokenService {

    private final ClientAppService clientAppService;
    private final AuthInternalApi authInternalApi;

    @Value("${openapi.client-token.app:sso}")
    private String tokenApp;

    /** 换票结果：token + 有效期秒数。 */
    public record TokenResult(String accessToken, long expiresIn) {}

    public TokenResult issue(String appCode, String appSecret) {
        if (appCode == null || appCode.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "app_code 与 app_secret 必填");
        }
        // 本地库验凭证（应用存在/启用/密钥摘要匹配）
        ApiClientApp app = clientAppService.verifyCredentials(appCode, appSecret);
        // perms 以应用授权为准（开放平台本地库唯一来源）
        List<String> perms = clientAppService.permissions(appCode);

        AuthInternalApi.ClientTokenRequest req = new AuthInternalApi.ClientTokenRequest();
        req.setAppId(app.getId());
        req.setAppCode(app.getAppCode());
        req.setApp(tokenApp);
        req.setPermissions(perms);

        R<Map<String, Object>> resp;
        try {
            resp = authInternalApi.issueClientToken(req);
        } catch (Exception e) {
            log.error("调用 sso-auth 签发第三方令牌失败：appCode={}", appCode, e);
            throw new BizException(OpenapiErrorCode.CHANNEL_ERROR, "令牌签发服务暂不可用");
        }
        if (resp == null || resp.getCode() != 0 || resp.getData() == null) {
            String msg = resp == null || resp.getMessage() == null ? "令牌签发失败" : resp.getMessage();
            throw new BizException(ErrorCode.BIZ_ERROR, msg);
        }
        Map<String, Object> data = resp.getData();
        Object token = data.get("accessToken");
        Object ttl = data.get("expiresIn");
        long expiresIn = ttl instanceof Number ? ((Number) ttl).longValue()
                : (ttl instanceof String ? Long.parseLong((String) ttl) : 3600L);
        log.info("第三方换票成功：appCode={} appId={} perms={}", appCode, app.getId(), perms);
        return new TokenResult(String.valueOf(token), expiresIn);
    }
}
