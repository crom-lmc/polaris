package com.laimengchao.sso.file.module.file.vo;

import lombok.Data;

/** 签名 URL 请求（服务内部持有，避免依赖 file-client DTO）。 */
@Data
public class SignRequest {

    private String purpose;

    private Long expiresSeconds;
}
