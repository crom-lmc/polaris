package com.laimengchao.sso.file.module.file.vo;

import lombok.Data;

import java.util.Map;

/** 批量保存附件全局设置。 */
@Data
public class AttachmentSettingsSaveRequest {

    /** key -> value */
    private Map<String, String> settings;
}
