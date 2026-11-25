package com.laimengchao.sso.audit.module.audit.vo;

import lombok.Data;

import java.util.Map;

/** 批量保存审计全局设置。 */
@Data
public class AuditSettingsSaveRequest {

    /** key -> value */
    private Map<String, String> settings;
}
