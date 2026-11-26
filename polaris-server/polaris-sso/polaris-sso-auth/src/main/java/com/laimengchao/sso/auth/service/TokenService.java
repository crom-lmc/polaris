package com.laimengchao.sso.auth.service;

import com.laimengchao.sso.auth.dto.AuthUser;
import com.laimengchao.sso.auth.dto.TokenResponse;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Map;

public interface TokenService {

    /**
     * 凭据校验（不签发）。之所以把「认证」和「签发」拆开：登录时要先建立中心会话拿到 sid，
     * 再把 sid 写进令牌，否则这一对令牌就不属于任何会话，登出时没法连带作废。
     */
    AuthUser authenticate(String username, String password, String clientIp, String userAgent, String loginType, String appCode);

    AuthUser authenticate(String username, String password, String clientIp, String userAgent, String loginType);

    /**
     * 刷新：验签 → 校验是 refresh 类型 → 校验未被吊销 → 从 account 取**最新**权限
     * → 旧 refresh 立即作废（轮换，防止被窃取后长期复用）。
     *
     * <p>刷新沿用原令牌的 {@code app} 声明：如果换成平台域，博客刷新一次就拿到了
     * 管理台权限，等于越权。
     */
    TokenResponse refresh(String refreshToken);

    /**
     * 刷新并校验令牌归属应用。
     *
     * @param expectedApp 期望的 app 声明；null 跳过校验（管理台自己的 /refresh 走这条路）。
     *                    OAuth2 标准端点用它把 refresh token 绑定到 client，防止
     *                    A 应用拿到的 refresh token 拿去 B 应用刷新（跨应用越权）
     */
    TokenResponse refreshForApp(String refreshToken, String expectedApp);

    /**
     * 登出：吊销 access / refresh 的 jti，并作废整个中心会话。
     * <p>
     * 关键在最后一步——只吊销 jti 的话，同一个浏览器里从别的应用换出来的令牌
     * （jti 不同、但同属一个 sid）依然有效，用户会觉得「退出了但系统还登着」。
     * sid 来源优先级：cookie 传进来的 > refresh 里的 claim > access 里的 claim。
     */
    void logout(String accessToken, String refreshToken);

    void logout(String accessToken, String refreshToken, String sid);

    /** 验签 access token，并确认类型正确、未被登出吊销（jti + 会话双重校验）。 */
    Claims parseAccess(String token);

    TokenResponse issuePair(AuthUser user);

    /** 签发一对令牌；sid 非空即绑定中心会话，用于跨应用集体登出。 */
    TokenResponse issuePair(AuthUser user, String sid);

    /** 会话是否已被作废；供授权码签发等入口在放行前自查 */
    boolean isSessionRevoked(String sid);

    /** 供网关同步吊销状态用的快照。 */
    Map<String, Instant> revokedSnapshot();

    long accessTtlSeconds();
}
