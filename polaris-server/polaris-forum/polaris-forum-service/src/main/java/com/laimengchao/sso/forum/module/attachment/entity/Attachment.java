package com.laimengchao.sso.forum.module.attachment.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class Attachment {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerAccountId;
    private String usageType;
    private String originalName;
    private String storagePath;
    /** 统一文件服务文件ID；为空表示仍用旧的本地存储文件 */
    private Long fileId;
    private String mimeType;
    private Long size;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long threadId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long draftId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long profileAccountId;
    private String createdAt;
}
