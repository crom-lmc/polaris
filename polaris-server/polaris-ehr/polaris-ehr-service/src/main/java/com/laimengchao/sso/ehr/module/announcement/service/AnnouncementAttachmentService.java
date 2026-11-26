package com.laimengchao.sso.ehr.module.announcement.service;

import com.laimengchao.sso.ehr.module.announcement.entity.AnnouncementAttachment;
import com.laimengchao.sso.ehr.module.announcement.vo.AnnouncementAttachmentVO;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface AnnouncementAttachmentService {

    @Data
    public static class AttachmentFile {
        private String fileName;
        private String mime;
        private byte[] bytes;
    }

    /** 上传得到临时附件（announcement_id=0），公告保存时再绑定真实 ID */
    AnnouncementAttachmentVO upload(MultipartFile file, Long accountId);

    /**
     * 公告保存时绑定附件：只接受尚未绑定的临时附件，防止拿别的公告的附件 ID 复用。
     * 本次没带上的旧附件会被移除（记录逻辑删 + 文件服务软删）。
     */
    void bind(Long announcementId, List<Long> attachmentIds);

    /** 批量取附件，返回 announcementId -> 列表 */
    Map<Long, List<AnnouncementAttachmentVO>> listByAnnouncementIds(List<Long> ids);

    /** 取字节：调用方必须先做公告可见性校验 */
    AttachmentFile content(Long attachmentId);

    /** 取字节并对图片/PDF 烧录下载水印（身份由调用方传入）。 */
    AttachmentFile content(Long attachmentId, String username, String userId, String clientIp);

    /** 公告删除时级联清理 */
    void deleteByAnnouncementIds(List<Long> ids);

    /** 公告所属附件 ID 列表，供可见性校验反查公告 */
    AnnouncementAttachment find(Long attachmentId);
}
