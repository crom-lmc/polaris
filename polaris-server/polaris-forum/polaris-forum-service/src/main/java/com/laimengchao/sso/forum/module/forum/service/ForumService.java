package com.laimengchao.sso.forum.module.forum.service;


import com.laimengchao.sso.forum.module.forum.dto.CommentCreateRequest;
import com.laimengchao.sso.forum.module.forum.dto.LikeToggleRequest;
import com.laimengchao.sso.forum.module.forum.dto.ThreadPublishRequest;
import com.laimengchao.sso.forum.module.forum.entity.ForumComment;
import com.laimengchao.sso.forum.module.forum.entity.ForumMember;
import com.laimengchao.sso.forum.module.forum.entity.ForumThread;
import com.laimengchao.sso.forum.module.forum.vo.CommentVO;
import com.laimengchao.sso.forum.module.forum.vo.ForumMeVO;
import com.laimengchao.sso.forum.module.forum.vo.LikeToggleVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadDetailVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadPageVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadVO;
import com.laimengchao.sso.forum.module.taxonomy.vo.BoardVO;
import java.util.List;
import java.util.Map;


public interface ForumService {

    List<BoardVO> boards();

    List<ThreadVO> list(String kind, String board, String keyword, String sort);

    /** 服务端分页列表：每页只查当前页且不查正文，避免一次返回全部内容导致接口慢、渲染卡。 */
    ThreadPageVO listPage(String kind, String board, String keyword, String sort, int page, int size);

    ThreadDetailVO detail(long id);

    List<CommentVO> comments(long threadId);

    ThreadDetailVO createThread(ThreadPublishRequest body);

    ThreadDetailVO publishDraft(ThreadPublishRequest body, long draftId);

    CommentVO createComment(long threadId, CommentCreateRequest body);

    LikeToggleVO toggleLike(LikeToggleRequest body);

    ForumMeVO me();

    ThreadVO threadView(ForumThread row);

    <T extends ThreadVO> T threadView(ForumThread row, T view);

    /** memberCache 为列表预加载的作者映射；为 null 时逐条查（单条详情场景保持原行为）。 */
    <T extends ThreadVO> T threadView(ForumThread row, T view, Map<Long, ForumMember> memberCache);

    CommentVO commentView(ForumComment row);

    CommentVO commentView(ForumComment row, Map<Long, ForumMember> memberCache);
}
