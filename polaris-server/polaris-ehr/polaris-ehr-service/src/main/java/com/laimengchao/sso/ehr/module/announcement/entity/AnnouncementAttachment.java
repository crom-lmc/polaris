package com.laimengchao.sso.ehr.module.announcement.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 内部通知附件：文件本体只在统一文件服务，本表只存 file_id 与展示元信息。
 * announcement_id=0 表示编辑中的临时附件，保存公告时绑定真实公告ID。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("announcement_attachment")
public class AnnouncementAttachment extends BaseEntity {

    /** 所属公告ID（ehr_announcement.id），0 表示尚未绑定 */
    private Long announcementId;
    /** 统一文件服务文件ID（file_object.id） */
    private Long fileId;
    private String fileName;
    private Long size;
    private String mime;
    /** 上传账号ID */
    private Long uploadedBy;
}
