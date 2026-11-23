package com.laimengchao.sso.client.dto;

import lombok.Data;

/**
 * 开放平台 → 账号中心：新增/编辑第三方对接应用。
 * appType 由账号中心强制为 OPENAPI_CLIENT，不接受调用方指定，避免误建内部应用。
 */
@Data
public class ApiAppMutation {

    /** 编辑时的应用编码；新增时必填 */
    private String code;
    private String name;
    private String baseUrl;
    private String redirectUri;
    private String remark;
    /** 1 启用 / 2 停用；新增时不传默认启用 */
    private Integer status;
    private Integer sort;
}
