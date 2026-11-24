package com.laimengchao.sso.ehr.module.announcement.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 内部通知附件（前端只拿 ID 与文件名，下载走受控接口） */
@Data
public class AnnouncementAttachmentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String fileName;
    private Long size;
    private String mime;
    private String downloadUrl;
    private LocalDateTime createdAt;
}
