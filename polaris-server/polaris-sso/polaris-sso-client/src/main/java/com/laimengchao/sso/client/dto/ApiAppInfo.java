package com.laimengchao.sso.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 开放平台 → 账号中心：第三方对接应用信息（服务间内部出参）。
 * appSecret 仅创建/重置时返回明文，其余场景为 null。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiAppInfo {

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
