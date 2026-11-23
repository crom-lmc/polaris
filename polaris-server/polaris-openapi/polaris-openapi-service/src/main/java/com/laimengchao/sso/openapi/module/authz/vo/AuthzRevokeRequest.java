package com.laimengchao.sso.openapi.module.authz.vo;

import lombok.Data;

/** 撤销授权请求。 */
@Data
public class AuthzRevokeRequest {

    private String appCode;
    private String apiCode;
}
