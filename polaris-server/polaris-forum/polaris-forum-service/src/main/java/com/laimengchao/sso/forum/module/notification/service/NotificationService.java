package com.laimengchao.sso.forum.module.notification.service;


import com.laimengchao.sso.forum.module.badge.entity.Badge;
import com.laimengchao.sso.forum.module.forum.entity.ForumComment;
import com.laimengchao.sso.forum.module.forum.entity.ForumThread;
import com.laimengchao.sso.forum.module.notification.vo.NotificationCountVO;
import com.laimengchao.sso.forum.module.notification.vo.NotificationReadAllVO;
import com.laimengchao.sso.forum.module.notification.vo.NotificationVO;
import com.laimengchao.sso.forum.module.report.entity.Report;
import java.util.List;
import java.util.Map;


public interface NotificationService {

    void onComment(ForumThread thread, ForumComment parent, ForumComment comment);

    /** 点赞通知：帖子赞通知作者，评论赞通知评论作者；bizId 用点赞记录ID避免同一目标多条通知重复。 */
    void onLike(long actorId, String targetType, long targetId, long likeId, long threadAuthorId, long commentAuthorId, long threadId);

    void onBadgeAwarded(long accountId, long memberBadgeId, Badge badge);

    void onReportResult(Report report);

    List<NotificationVO> list(String type, Boolean unread);

    NotificationCountVO unreadCount();

    /** 各类型未读数（含全部合计），供消息中心分类角标展示。 */
    Map<String, Integer> unreadCounts();

    void markRead(long id);

    NotificationReadAllVO markAllRead();
}
