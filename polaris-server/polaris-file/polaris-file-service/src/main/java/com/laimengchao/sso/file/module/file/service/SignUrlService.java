package com.laimengchao.sso.file.module.file.service;

public interface SignUrlService {

    /** 生成签名 URL 与过期时间（epoch 秒）。 */
    String[] sign(String fileId, String purpose, Long expiresSeconds);

    /** 校验签名；过期或签名不符抛 403。 */
    void verify(String fileId, String purpose, long expiresAt, String signature);
}
