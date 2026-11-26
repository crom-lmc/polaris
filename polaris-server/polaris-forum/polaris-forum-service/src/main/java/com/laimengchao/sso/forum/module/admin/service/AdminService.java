package com.laimengchao.sso.forum.module.admin.service;


import com.laimengchao.sso.forum.module.admin.dto.CommentDeleteRequest;
import com.laimengchao.sso.forum.module.admin.dto.ExchangeFulfillmentRequest;
import com.laimengchao.sso.forum.module.admin.dto.GiftRequest;
import com.laimengchao.sso.forum.module.admin.dto.SensitiveWordEnabledRequest;
import com.laimengchao.sso.forum.module.admin.dto.SensitiveWordRequest;
import com.laimengchao.sso.forum.module.admin.dto.ThreadStatusUpdateRequest;
import com.laimengchao.sso.forum.module.admin.vo.AdminCommentPageVO;
import com.laimengchao.sso.forum.module.admin.vo.AdminCommentVO;
import com.laimengchao.sso.forum.module.admin.vo.AdminThreadPageVO;
import com.laimengchao.sso.forum.module.admin.vo.AdminThreadVO;
import com.laimengchao.sso.forum.module.admin.vo.SensitiveWordPageVO;
import com.laimengchao.sso.forum.module.admin.vo.SensitiveWordVO;
import com.laimengchao.sso.forum.module.forum.entity.ForumExchange;
import com.laimengchao.sso.forum.module.forum.entity.ForumGift;
import java.util.List;


public interface AdminService {

    List<AdminThreadVO> threads(String kind, String board, String keyword, Boolean deleted);

    /** 内容分页：服务端 LIMIT + 总数，列表不查正文，避免一次返回全部帖子拖慢管理端。 */
    AdminThreadPageVO threadsPage(String kind, String board, String keyword, Boolean deleted, int page, int size);

    AdminThreadVO updateThread(long id, ThreadStatusUpdateRequest body);

    List<AdminCommentVO> comments(Long threadId, String keyword, Boolean deleted);

    /** 评论分页：服务端 LIMIT + 总数，避免一次返回上万条评论正文拖慢管理端。 */
    AdminCommentPageVO commentsPage(Long threadId, String keyword, Boolean deleted, int page, int size);

    AdminCommentVO setCommentDeleted(long id, CommentDeleteRequest body);

    List<SensitiveWordVO> sensitiveWords();

    /** 敏感词分页：服务端 LIMIT + 总数，避免一次返回全部敏感词拖慢管理端。 */
    SensitiveWordPageVO sensitiveWordsPage(int page, int size);

    SensitiveWordVO addSensitiveWord(SensitiveWordRequest body);

    void setSensitiveWordEnabled(long id, SensitiveWordEnabledRequest body);

    void deleteSensitiveWord(long id);

    List<ForumGift> adminGifts();

    ForumGift createGift(GiftRequest body);

    ForumGift updateGift(long id, GiftRequest body);

    List<ForumExchange> exchanges(String status);

    ForumExchange shipExchange(long id, ExchangeFulfillmentRequest body);

    ForumExchange issueExchange(long id, ExchangeFulfillmentRequest body);
}
