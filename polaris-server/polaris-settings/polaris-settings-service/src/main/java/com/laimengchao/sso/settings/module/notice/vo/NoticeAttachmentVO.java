package com.laimengchao.sso.settings.module.notice.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内消息附件（前端只拿到 ID 与文件名，下载走受控接口） */
@Data
public class NoticeAttachmentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String fileName;
    private Long size;
    private String mime;
    /** 相对下载路径，前端拼当前网关前缀即可 */
    private String downloadUrl;
    private LocalDateTime createdAt;
}
