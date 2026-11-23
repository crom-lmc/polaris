package com.laimengchao.sso.account.dto.request;

import lombok.Data;

/**
 * 开放平台内部接口：新增/编辑第三方对接应用。
 * appType 由服务端强制为 OPENAPI_CLIENT，不接受调用方指定。
 */
@Data
public class InternalClientAppRequest {

    private String code;
    private String name;
    private String baseUrl;
    private String redirectUri;
    private String remark;
    private Integer status;
    private Integer sort;
}
