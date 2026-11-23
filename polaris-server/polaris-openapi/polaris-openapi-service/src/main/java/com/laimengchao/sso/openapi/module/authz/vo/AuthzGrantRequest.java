package com.laimengchao.sso.openapi.module.authz.vo;

import lombok.Data;

import java.util.List;

/** 新增授权请求：给一个第三方应用批量授权若干开放接口。 */
@Data
public class AuthzGrantRequest {

    private String appCode;
    /** 开放接口目录的 api_code 列表 */
    private List<String> apiCodes;
}
