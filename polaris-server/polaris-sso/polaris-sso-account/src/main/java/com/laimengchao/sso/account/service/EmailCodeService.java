package com.laimengchao.sso.account.service;

public interface EmailCodeService {

    /** 生成 6 位验证码，哈希入库，返回明文（供 auth 发送）。 */
    String create(String email, String scene, String ip);

    /**
     * 校验验证码，返回通过该邮箱登录的账户ID。失败抛业务异常。
     * <p>多次错误会直接作废该条验证码（防爆破）。</p>
     */
    Long verify(String email, String scene, String code);

    /** 让某邮箱下未使用的验证码失效（改绑邮箱等场景可选调用）。 */
    void invalidate(String email, String scene);
}
