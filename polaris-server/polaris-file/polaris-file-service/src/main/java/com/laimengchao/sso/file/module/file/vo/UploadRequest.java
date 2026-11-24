package com.laimengchao.sso.file.module.file.vo;

import lombok.Data;

/** 管理端/内部上传请求，内容与 DTO 同构，服务内部独立持有避免依赖 file-client。 */
@Data
public class UploadRequest {

    private String bizType;

    private String visibility;

    private String originalName;

    private String contentType;

    /** base64 内容 */
    private String content;

    private String ownerType;

    private String ownerId;

    private Long uploaderId;
}
