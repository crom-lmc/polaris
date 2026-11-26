package com.laimengchao.sso.auth.service.oauth;


import com.laimengchao.sso.auth.dto.AppBrief;
import java.util.List;


public interface OAuthService {

    public record AuthorizeResult(String code, String redirectTo, long expiresIn) {
    }

    /** 不带中心会话的签发（服务间/无 cookie 场景），换出的令牌不参与集体登出 */
    AuthorizeResult issue(Long accountId, String clientId, String redirectUri, String scope, String state, String codeChallenge, String codeChallengeMethod, List<AppBrief> myApps);

    /**
     * 签发授权码（authorize 端点，用户身份由中心会话/Bearer 提供）。
     *
     * @param myApps 当前账号可访问的应用——clientId 必须在其中（应用存在 + 用户有授权，一次校验完成）
     * @param sid    授权时所在的中心会话，写进码里由 token 端点继承，用于跨应用集体登出
     */
    AuthorizeResult issue(Long accountId, String clientId, String redirectUri, String scope, String state, String codeChallenge, String codeChallengeMethod, List<AppBrief> myApps, String sid);

    /** 消费授权码（token 端点）：取走即删，一次性。 */
    OAuthCodeStore.CodePayload take(String code);

    /**
     * PKCE 校验：S256 = BASE64URL(SHA256(verifier))；plain = 原值比对。
     * 有 challenge 的码必须带 verifier，缺了直接拒——这是公共客户端的防截获底线。
     */
    boolean verifyPkce(OAuthCodeStore.CodePayload payload, String verifier);

    /** 换码端点用的最终跳转地址；state 原样带回（CSRF 防护靠调用方校验它） */
    String buildRedirect(String redirectUri, String code, String state);
}
