package com.laimengchao.sso.cms.module.content.service;

import com.laimengchao.sso.cms.module.content.dto.*;
import com.laimengchao.sso.common.R;

import java.util.List;

public interface ArticleService {

    ArticleVO save(ArticleSaveRequest req);

    ArticleVO detail(Long id);

    /** 管理端分页：status=null 全部（不含回收站），-1=回收站 */
    R.Page<ArticleVO> page(ArticleQuery q);

    /**
     * 提审：草稿/驳回 → 审批中(6)，并向审批中心发起 {@code cms.article_publish} 实例。
     * <p>
     * <b>刻意不加 {@code @Transactional}</b>：settings 在模板没有审批人时会当场把实例推到终态
     * 并<b>同步</b>回调本服务；若本地状态还在未提交的事务里，回调线程读不到这条文章，
     * 会按「文章不存在」返回、审批单却已终结，文章就永远卡在「审批中」。
     * 所以这里先独立提交「审批中」，再远程发起。
     */
    void submit(Long id, String note);

    /**
     * 接收审批中心回传的终审结果。<b>必须幂等</b>：回调失败会退避重试。
     * 通过 → 发布（有未来定时发布时间则转待发布）；驳回 → 4；撤回/终止 → 回草稿。
     */
    void handleApprovalResult(ApprovalCallbackRequest req);

    /**
     * 作者撤回审批中的稿件：先撤审批中心的在途单，再退回草稿。
     * <p>刻意不加事务：撤回时 settings 会同步回调本服务，回调线程要 UPDATE 同一行。
     */
    void withdraw(Long id);

    /** 审核通过 → 发布（写版本快照）。仅历史「1 待审核」单据可用；新单据在审批中心处理 */
    void approve(Long id, String note);

    /** 驳回：退回给作者改后再提审（历史本地审核单据） */
    void reject(Long id, String note);

    /**
     * 发布：只允许「审批通过后的待发布」稿件发布（含定时待发布可提前手动发）。
     * 草稿 / 驳回 / 审批中一律拒绝——后端也要堵死「跳过审核直接发布」，不能只靠隐藏按钮。
     */
    void publishNow(Long id);

    /** 下线：从前台撤下，不进回收站 */
    void offline(Long id);

    /** 置顶/取消置顶；推荐位同理 */
    void setFlag(Long id, String flag, boolean on);

    void toRecycle(Long id);

    void restore(Long id);

    void deleteForever(Long id);

    List<VersionVO> versions(Long articleId);


    /** 一键回滚：当前内容先自动快照，再写入目标版本内容 */
    void rollback(Long articleId, Long versionId);

    /** 定时发布调度：到点的待发布文章逐篇发布（调度入口见 schedule 包的 ScheduledPublishJob，每 30 秒） */
    int publishDueScheduled();

    /** 前台文章列表：已发布，置顶优先 */
    R.Page<ArticleVO> publicPage(String keyword, Long categoryId, Long tagId, int page, int size);

    /** 全文检索：ngram FULLTEXT，异常回退 LIKE */
    List<ArticleVO> search(String keyword, int limit);

    ArticleVO publicDetail(Long id);
}
