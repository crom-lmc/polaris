package com.laimengchao.sso.settings.module.notice.dto;

import lombok.Data;

/**
 * 站内消息附件入参：业务方先把文件上传到统一文件服务（bizType=notice_attachment），
 * 再把返回的 file_id 与文件元信息随消息一起传进来，本服务只建关联、不接收文件字节。
 */
@Data
public class NoticeAttachmentDTO {

    /** 统一文件服务文件ID（file_object.id） */
    private Long fileId;
    /** 展示用原始文件名 */
    private String fileName;
    private Long size;
    private String mime;
}
