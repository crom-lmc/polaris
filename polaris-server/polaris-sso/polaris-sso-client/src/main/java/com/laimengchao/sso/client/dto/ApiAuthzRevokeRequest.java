package com.laimengchao.sso.client.dto;

import lombok.Data;

/** 开放平台 → 账号中心：撤销第三方应用的接口权限码。 */
@Data
public class ApiAuthzRevokeRequest {

    private String appCode;
    private String code;
}
