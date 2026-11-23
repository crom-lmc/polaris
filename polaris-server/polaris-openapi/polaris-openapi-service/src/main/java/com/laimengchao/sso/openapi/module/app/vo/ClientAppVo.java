package com.laimengchao.sso.openapi.module.app.vo;

import lombok.Data;

/** 第三方对接应用（开放平台管理端展示用）。 */
@Data
public class ClientAppVo {

    private Long id;
    private String code;
    private String name;
    private String baseUrl;
    private String redirectUri;
    private String remark;
    /** 1 启用 / 2 停用 */
    private Integer status;
    private Integer sort;
    /** 仅创建/重置返回一次明文密钥 */
    private String appSecret;
}
