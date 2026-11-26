package com.laimengchao.sso.settings.module.notice.service;

import com.laimengchao.sso.settings.module.notice.dto.NoticeAttachmentDTO;
import com.laimengchao.sso.settings.module.notice.vo.NoticeAttachmentVO;
import lombok.Data;
import java.util.List;
import java.util.Map;

public interface NoticeAttachmentService {

    /** 下载结果：文件名 / MIME / 字节 */
    @Data
    public static class AttachmentFile {
        private String fileName;
        private String mime;
        private byte[] bytes;
    }

    /** 消息落库后绑定附件；file_id 非法或为空的项直接跳过，不让脏数据进库 */
    void bind(Long noticeId, List<NoticeAttachmentDTO> list, Long uploaderId);

    /** 批量取附件，返回 noticeId -> 附件列表；消息列表一次查询避免 N+1 */
    Map<Long, List<NoticeAttachmentVO>> listByNoticeIds(List<Long> noticeIds);

    /** 下载：先确认附件所属消息确实发给当前账号，再向统一文件服务取字节 */
    AttachmentFile download(Long attachmentId, Long accountId);

    /** 下载并对图片/PDF 烧录水印（身份由调用方传入）。 */
    AttachmentFile download(Long attachmentId, Long accountId, String username, String userId, String clientIp);

}
