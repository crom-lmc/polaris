package com.laimengchao.sso.ehr.module.announcement.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.announcement.dto.AnnouncementSaveRequest;
import com.laimengchao.sso.ehr.module.announcement.vo.AnnouncementVO;

public interface AnnouncementService {

    R.Page<AnnouncementVO> page(Integer status, String category, String keyword, int page, int size);

    AnnouncementVO detail(Long id);

    AnnouncementVO create(AnnouncementSaveRequest req);

    /** 已发布的通知必须先下架才能编辑，避免线上内容被半截改写 */
    AnnouncementVO update(Long id, AnnouncementSaveRequest req);

    AnnouncementVO publish(Long id);

    AnnouncementVO offline(Long id);

    void delete(Long id);

    R.Page<AnnouncementVO> myPage(int page, int size, String category, String keyword, boolean unreadOnly);

    /** 打开详情即记读；只有首次打开才给冗余的阅读人数 +1 */
    AnnouncementVO myDetail(Long id);

    /**
     * 附件下载的可见性入口：供附件 Controller 调用，避免把 myOrgScope/visibleTo 暴露成通用工具。
     * 只有「已发布 + 在当前用户可见范围内」的公告附件才允许读取。
     */
    boolean canAccessAttachment(Long attachmentId, Long accountId);

    long myUnreadCount();
}
