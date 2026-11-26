package com.laimengchao.sso.settings.module.approval.service;

import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;

public interface ApprovalCallbackService {

    /**
     * 落一条待回调记录，并在事务提交后立即投递一次。
     * <p>
     * 必须在 afterCommit 里发：业务方收到回调会去查审批单状态，
     * 如果这时事务还没提交，它查到的是「进行中」，会误判。
     */
    void enqueue(ApprInstance inst);

    /** 立即投递一次 */
    void dispatch(Long callbackId);

    /** 扫描待重试的回调，退避间隔 1/2/4/8/16/30/30… 分钟（调度入口见 schedule 包的 ApprovalCallbackRetryJob） */
    void retryFailed();

    /** 人工重试入口（审批中心「重新通知业务方」按钮） */
    void retryNow(Long instanceId);
}
