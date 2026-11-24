package com.laimengchao.sso.file.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 临时访问 URL 结果。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignUrlResult {

    private String url;

    /** 过期时间（epoch 秒） */
    private Long expiresAt;
}
