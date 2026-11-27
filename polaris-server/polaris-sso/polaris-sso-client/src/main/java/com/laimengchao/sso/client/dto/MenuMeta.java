package com.laimengchao.sso.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 菜单元数据契约（sso-account → sso-auth）。本模块刻意不依赖 common，故在此独立定义。
 * 字段与 auth 侧的 {@code com.laimengchao.sso.common.security.MenuMeta} 一一对应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MenuMeta {

    private String code;
    private String name;
    private int sort;
    private String parentCode;
}
