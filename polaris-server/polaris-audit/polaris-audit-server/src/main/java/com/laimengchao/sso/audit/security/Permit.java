package com.laimengchao.sso.audit.security;

import com.laimengchao.sso.common.security.PermissionCodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端按钮级权限注解。app 默认仅允许 audit 应用令牌；
 * 审计前端目前复用统一登录中心直连登录（app_code=sso），故相关接口显式放行 sso。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Permit {

    String[] value();

    /** 允许访问的令牌应用域，默认审计中心 */
    String[] app() default {PermissionCodes.APP_AUDIT};

    Logical logical() default Logical.ANY;

    enum Logical {
        ANY, ALL
    }
}
