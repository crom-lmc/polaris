package com.laimengchao.sso.openapi.security;


import com.laimengchao.sso.openapi.security.dto.CallerIdentity;


public interface AccessTokenService {

    /** 签发短期令牌。 */
    String issue(CallerIdentity identity);

    /** 验签并还原调用方身份。签名错误/过期一律 401。 */
    CallerIdentity verify(String token);

    long ttlSeconds();
}
