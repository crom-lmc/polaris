package com.laimengchao.sso.openapi.module.setting.vo;

import lombok.Data;

/** 第三方应用的来源 IP 白名单（管理端展示 + 维护）。 */
@Data
public class AppIpWhitelistVo {

    private String appCode;
    private String appName;
    /** 应用级白名单（换行分隔），为空表示回退全局白名单 */
    private String whitelist;
    /** 当前实际生效的白名单来源：APP=应用级 / GLOBAL=全局回退 / NONE=不限制 */
    private String effectiveFrom;
}
