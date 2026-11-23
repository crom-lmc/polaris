package com.laimengchao.sso.auth.controller;

import com.laimengchao.sso.auth.dto.ClientTokenRequest;
import com.laimengchao.sso.auth.dto.TokenResponse;
import com.laimengchao.sso.auth.service.JwtService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.JwtClaims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第三方应用机器令牌签发（client_credentials）。
 * <p>
 * 路径在网关 internalPrefixes（/api/auth/internal/）下，外部请求已被网关拒绝；
 * 这里再校验 X-Internal-Token 做双保险。签发用 RS256 私钥，sub=应用数字 ID，
 * 只签 access 不签 refresh——第三方过期后凭凭证重新换票。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/internal")
@RequiredArgsConstructor
public class ClientTokenController {

    private final JwtService jwtService;

    @Value("${sso.client.internal-token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    @PostMapping("/client-token")
    public R<TokenResponse> issue(@RequestHeader(value = JwtClaims.Headers.INTERNAL_TOKEN, required = false) String token,
                                  @RequestBody ClientTokenRequest req) {
        if (internalToken == null || internalToken.isBlank()
                || token == null || !internalToken.equals(token)) {
            throw new BizException(ErrorCode.FORBIDDEN, "内部令牌无效");
        }
        if (req.getAppId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appId 必填");
        }
        if (req.getAppCode() == null || req.getAppCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }
        JwtService.Issued access = jwtService.issueClientToken(
                req.getAppId(), req.getAppCode(), req.getApp(), req.getPermissions());
        TokenResponse resp = new TokenResponse(access.token(), null, "Bearer", access.ttlSeconds(), null);
        log.info("签发第三方应用令牌：appCode={} appId={} perms={}",
                req.getAppCode(), req.getAppId(), req.getPermissions());
        return R.ok(resp);
    }
}
