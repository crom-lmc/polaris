package com.laimengchao.sso.openapi.module.authz.vo;

import lombok.Data;

/** 启停授权请求。 */
@Data
public class AuthzToggleRequest {

    private String appCode;
    private String apiCode;
    /** 1=启用 2=停用 */
    private Integer status;
}
