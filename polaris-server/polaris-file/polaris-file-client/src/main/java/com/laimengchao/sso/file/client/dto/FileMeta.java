package com.laimengchao.sso.file.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 文件元数据（不含文件字节）。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileMeta {

    /** 雪花 ID，JSON 中为字符串 */
    private String id;

    private String bizType;

    /** PUBLIC / PRIVATE */
    private String visibility;

    private String originalName;

    private String contentType;

    private Long size;

    private String sha256;

    private String ownerType;

    private String ownerId;

    /** NORMAL / DELETED */
    private String status;

    /** 签名/公开访问 URL，由服务端按 visibility 生成 */
    private String url;

    private String createdAt;
}
