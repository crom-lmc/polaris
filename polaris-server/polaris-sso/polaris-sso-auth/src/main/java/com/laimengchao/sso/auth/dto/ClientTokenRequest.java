package com.laimengchao.sso.auth.dto;

import lombok.Data;

import java.util.List;

/** 第三方应用机器令牌签发请求（供开放平台 openapi-auth 内网调用）。 */
@Data
public class ClientTokenRequest {
    /** 第三方应用数字 ID，作为令牌 sub */
    private Long appId;
    /** 第三方应用编码，写入 client_app_code claim */
    private String appCode;
    /** 令牌作用域应用编码（openapi） */
    private String app;
    /** 应用被授权的权限码（perms 唯一来源，由 openapi 提供） */
    private List<String> permissions;
}
