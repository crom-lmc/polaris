package com.laimengchao.sso.file.client.dto;

import lombok.Data;

/** 旧路径别名注册请求（存量迁移）。 */
@Data
public class FileAliasRequest {
    private String oldPath;
    private String fileId;
    private String sourceApp;
}
