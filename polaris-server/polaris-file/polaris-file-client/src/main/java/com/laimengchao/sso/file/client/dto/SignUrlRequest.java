package com.laimengchao.sso.file.client.dto;

import lombok.Data;

/** 生成临时访问 URL 的请求。 */
@Data
public class SignUrlRequest {

    /** VIEW（内联预览）/ DOWNLOAD（强制下载） */
    private String purpose;

    /** 有效期秒数，服务端有上限（默认 900 秒） */
    private Long expiresSeconds;
}
