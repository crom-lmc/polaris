package com.laimengchao.sso.openapi.security;


import com.laimengchao.sso.openapi.security.dto.CallerIdentity;


public interface AuthService {

    /** 换令牌结果。 */
    public record TokenResult(String accessToken, long expiresIn) {
    }


    /** 签名模式鉴权：校验时间戳/nonce，转发账号中心验签，通过后返回调用方身份。 */
    CallerIdentity authenticateSignature(String appCode, String timestamp, String nonce, String content, String signature);
}
