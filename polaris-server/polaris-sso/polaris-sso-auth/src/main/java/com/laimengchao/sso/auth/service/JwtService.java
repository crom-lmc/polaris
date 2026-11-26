package com.laimengchao.sso.auth.service;

import com.laimengchao.sso.auth.dto.AuthUser;
import io.jsonwebtoken.Claims;
import java.security.PublicKey;
import java.util.List;

public interface JwtService {

    /** 签发结果：令牌本体 + jti（用于吊销）+ 有效期秒数。 */
    public record Issued(String token, String jti, long ttlSeconds) {
    }

    /** 签发 access token，携带账户身份与权限码。 */
    Issued issueAccess(AuthUser user);

    /** 签发 access token，并绑定中心会话（sid 用于跨应用集体登出）。 */
    Issued issueAccess(AuthUser user, String sid);

    /** 签发 refresh token，不带权限码（只用于换新 access）。 */
    Issued issueRefresh(AuthUser user);

    Issued issueRefresh(AuthUser user, String sid);

    /**
     * 签发第三方应用机器令牌（client_credentials 场景）。
     * <p>
     * 与登录令牌共用同一把 RS256 私钥、同一套基础 claim，但有三点不同：
     * ① sub 传的是应用数字 ID（{@code appId}），不是账号 ID；
     * ② 附加 {@code client_app_code}（对外编码）与 {@code purpose=client_app}，供开放平台隔离人机令牌；
     * ③ 只签 access，不签 refresh（第三方过期后凭凭证重新换票）。
     */
    Issued issueClientToken(Long appId, String appCode, String app, List<String> perms);

    /** 公钥（用于 /api/auth/public/jwks 与公钥分发，业务应用可脱离网关自校验令牌） */
    PublicKey publicKey();

    /** 验签并解析；非法/过期会抛 io.jsonwebtoken.JwtException。 */
    Claims parse(String token);
}
