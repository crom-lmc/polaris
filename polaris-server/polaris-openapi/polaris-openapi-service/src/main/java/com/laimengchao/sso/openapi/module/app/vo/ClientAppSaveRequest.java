package com.laimengchao.sso.openapi.module.app.vo;

import lombok.Data;

/** 开放平台管理端：新增/编辑第三方对接应用请求。 */
@Data
public class ClientAppSaveRequest {

    /** 编辑时必填；新增时必填（创建后不可改） */
    private String code;
    private String name;
    private String baseUrl;
    private String redirectUri;
    private String remark;
    private Integer status;
    private Integer sort;
}
