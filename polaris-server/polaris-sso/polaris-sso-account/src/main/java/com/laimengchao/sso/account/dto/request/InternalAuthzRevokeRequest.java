package com.laimengchao.sso.account.dto.request;

import lombok.Data;

/** 开放平台内部接口：撤销第三方应用的某个接口权限码。 */
@Data
public class InternalAuthzRevokeRequest {

    private String appCode;
    private String code;
}
