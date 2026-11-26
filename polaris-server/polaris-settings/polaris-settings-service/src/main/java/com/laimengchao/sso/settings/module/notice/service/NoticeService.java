package com.laimengchao.sso.settings.module.notice.service;

import com.laimengchao.sso.settings.module.notice.dto.NoticeSendRequest;
import com.laimengchao.sso.settings.module.notice.entity.NoticeRule;
import com.laimengchao.sso.settings.module.notice.vo.NoticeMessageVO;
import com.laimengchao.sso.settings.module.notice.vo.NoticeRuleVO;

import java.util.List;
import java.util.Map;

public interface NoticeService {

    /**
     * 按事件编码发送。规则停用或不存在且调用方未给兜底内容时返回 false，不产生任何数据。
     * <p>
     * 不加 @Transactional：调用方（审批引擎）在自己事务里调用，这里若抛异常被外层 catch，
     * 事务已标记 rollback-only，会把审批主流程一起回滚。单条 insert + 已兜底的邮件足够安全。
     */
    boolean send(NoticeSendRequest req);

    /**
     * 异步发送（专供 SLA 批量扫描等定时场景）：在 noticeAsyncExecutor 线程池执行 send，
     * 避免定时任务线程被逐条站内信/邮件阻塞。调用方需从外部 bean 调用本方法才会走代理生效。
     * 同步业务（审批引擎主流程）仍走 {@link #send(NoticeSendRequest)}，行为不变。
     */
    void sendAsync(NoticeSendRequest req);

    /**
     * 群发：同一条业务待办发给多个账号（如「HR 部门全员」）。
     * 逐条落库，返回成功条数；单条失败不影响其它人。
     */
    int sendBatch(NoticeSendRequest req);

    List<NoticeRuleVO> listRules(String keyword);

    NoticeRuleVO createRule(NoticeRule req);

    NoticeRuleVO updateRule(Long id, NoticeRule req);

    void deleteRule(Long id);

    List<NoticeMessageVO> myMessages(Long accountId, Integer kind, Boolean unreadOnly, Integer limit);

    /** 审批单办结后，把该单下的待办消息一并置为已读，前端待办自然消失 */
    int markBizHandled(String bizType, String bizId);

    long unreadCount(Long accountId, Integer kind);

    int markRead(Long accountId, List<Long> ids);

    int markAllRead(Long accountId);

    /** 取规则上配置的指定接收人（账号 ID 列表 + 组织节点 ID 列表），供业务方自行展开部门。 */
    Map<String, List<Long>> ruleReceivers(String code);
}
