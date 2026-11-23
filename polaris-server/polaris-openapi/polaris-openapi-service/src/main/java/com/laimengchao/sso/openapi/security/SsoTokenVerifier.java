package com.laimengchao.sso.openapi.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.security.dto.CallerIdentity;
import com.laimengchao.sso.utils.KeySources;
import com.laimengchao.sso.utils.PemUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * sso-auth RS256 机器令牌验签器。
 * <p>
 * 第三方凭 appCode+secret 经 openapi-auth 换来的令牌由 sso-auth 用 RS256 签发：
 * sub=应用数字 ID、client_app_code=对外编码、purpose=client_app、perms=应用授权码。
 * 本类持公钥验签并隔离人机令牌（只认 purpose=client_app），验出的 perms 直接作为
 * 开放接口的调用权限，账号中心与 HS256 自签令牌都不再参与。
 */
@Slf4j
@Component
public class SsoTokenVerifier {

    private final PublicKey publicKey;
    private final String issuer;

    public SsoTokenVerifier(@Value("${sso.auth.public-key:${SSO_JWT_PUBLIC_KEY:classpath:keys/jwt-public.pem}}") String publicKeySpec,
                            @Value("${sso.auth.issuer:${SSO_AUTH_ISSUER:sso-auth}}") String issuer) {
        try (InputStream in = KeySources.open(publicKeySpec)) {
            this.publicKey = PemUtils.parsePublicKey(in);
        } catch (Exception e) {
            throw new IllegalStateException("加载 sso-auth JWT 公钥失败：" + e.getMessage(), e);
        }
        this.issuer = issuer;
    }

    /**
     * 校验并解析第三方机器令牌；不是 client_app 用途或验签失败一律 TOKEN_INVALID。
     *
     * @return openapi 侧的调用身份，appCode 取 client_app_code，perms 取 token 中的授权码
     */
    public CallerIdentity verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!JwtClaims.TYPE_ACCESS.equals(claims.get(JwtClaims.TOKEN_TYPE, String.class))) {
                throw new BizException(OpenapiErrorCode.TOKEN_INVALID, "令牌类型无效");
            }
            String purpose = claims.get(JwtClaims.TOKEN_PURPOSE, String.class);
            if (!JwtClaims.PURPOSE_CLIENT_APP.equals(purpose)) {
                throw new BizException(OpenapiErrorCode.TOKEN_INVALID, "令牌用途不匹配");
            }
            String appCode = claims.get(JwtClaims.CLIENT_APP_CODE, String.class);
            if (appCode == null || appCode.isBlank()) {
                throw new BizException(OpenapiErrorCode.TOKEN_INVALID, "令牌缺少应用编码");
            }
            String permsCsv = claims.get(JwtClaims.PERMISSIONS, String.class);
            Set<String> perms = new HashSet<>();
            if (permsCsv != null && !permsCsv.isBlank()) {
                Arrays.stream(permsCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(perms::add);
            }
            return new CallerIdentity(appCode, perms);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("第三方机器令牌验签失败：{}", e.getMessage());
            throw new BizException(OpenapiErrorCode.TOKEN_INVALID);
        }
    }
}
