package com.laimengchao.sso.client.dto;

import lombok.Data;

import java.util.List;

/** 开放平台 → 账号中心：给第三方应用授予接口权限码。 */
@Data
public class ApiAuthzGrantRequest {

    private String appCode;
    private List<PermItem> items;

    @Data
    public static class PermItem {
        private String code;
        private String name;
    }
}
