package com.laimengchao.sso.account.dto.request;

import lombok.Data;

import java.util.List;

/** 开放平台内部接口：给第三方应用授予接口权限码。 */
@Data
public class InternalAuthzGrantRequest {

    private String appCode;
    private List<PermItem> items;

    @Data
    public static class PermItem {
        private String code;
        private String name;
    }
}
