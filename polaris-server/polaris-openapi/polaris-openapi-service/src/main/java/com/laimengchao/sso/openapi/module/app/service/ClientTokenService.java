package com.laimengchao.sso.openapi.module.app.service;

public interface ClientTokenService {

    /** 换票结果：token + 有效期秒数。 */
    public record TokenResult(String accessToken, long expiresIn) {}

    TokenResult issue(String appCode, String appSecret);
}
