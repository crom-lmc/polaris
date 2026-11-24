package com.laimengchao.sso.settings.client.dto;

import lombok.Data;

/**
 * 站内消息附件入参（跨服务传输）：调用方先上传到统一文件服务，
 * 再把 file_id 与元信息随 send / sendBatch 一起传过来。
 */
@Data
public class NoticeAttachmentDTO {

    /** 统一文件服务文件ID（file_object.id） */
    private Long fileId;
    private String fileName;
    private Long size;
    private String mime;
}
