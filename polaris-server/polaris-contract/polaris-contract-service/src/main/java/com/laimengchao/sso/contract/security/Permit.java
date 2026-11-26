package com.laimengchao.sso.contract.security;

import com.laimengchao.sso.common.security.PermissionCodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 管理端按钮级权限注解，权限码常量见 {@link PermissionCodes}。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Permit {

    String[] value();

    /** 允许访问的令牌应用域，默认合同服务管理端 */
    String app() default PermissionCodes.APP_CONTRACT;

    Logical logical() default Logical.ANY;

    enum Logical {
        ANY, ALL
    }
}
