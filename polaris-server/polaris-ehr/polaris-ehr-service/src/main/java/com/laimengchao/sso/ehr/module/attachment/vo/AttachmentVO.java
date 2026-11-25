package com.laimengchao.sso.ehr.module.attachment.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttachmentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 统一文件服务文件 ID（旧存量附件可能为 null，此时不可分享） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;
    private String fileName;
    private Long size;
    private String mime;
    private String bizType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
