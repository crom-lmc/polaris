package com.laimengchao.sso.openapi.module.app.vo;

import lombok.Data;

/** 启用中的应用下拉选项（API 归属应用选择用，含内部系统应用）。 */
@Data
public class AppOptionVo {

    private String code;
    private String name;
}
