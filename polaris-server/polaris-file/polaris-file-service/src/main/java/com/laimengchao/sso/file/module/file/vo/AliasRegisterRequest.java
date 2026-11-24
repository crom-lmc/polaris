package com.laimengchao.sso.file.module.file.vo;

import lombok.Data;

/** 旧路径别名注册请求（迁移脚本调用）。 */
@Data
public class AliasRegisterRequest {
    private String oldPath;
    private String fileId;
    private String sourceApp;
}
