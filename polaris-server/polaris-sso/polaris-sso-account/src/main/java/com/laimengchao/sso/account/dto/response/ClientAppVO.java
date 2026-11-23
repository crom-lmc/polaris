package com.laimengchao.sso.account.dto.response;

import lombok.Data;

/**
 * 开放平台内部接口：第三方对接应用信息。字段名与 sso-client 的 ApiAppInfo 对齐，
 * Feign 按 JSON 字段映射。appSecret 仅创建/重置时返回明文。
 */
@Data
public class ClientAppVO {

    private Long id;
    private String code;
    private String name;
    private String appType;
    private String baseUrl;
    private String redirectUri;
    private String remark;
    private Integer status;
    private Integer sort;
    /** 仅创建/重置返回一次明文 */
    private String appSecret;
}
