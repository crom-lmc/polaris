package com.laimengchao.sso.settings.module.notice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内消息附件：只存统一文件服务的 file_id，不外露存储路径。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notice_attachment")
public class NoticeAttachment extends BaseEntity {

    /** 所属站内消息ID（notice_message.id） */
    private Long noticeId;
    /** 统一文件服务文件ID（file_object.id） */
    private Long fileId;
    private String fileName;
    private Long size;
    private String mime;
    /** 上传账号ID，系统生成的消息可为 0 */
    private Long uploadedBy;
}
