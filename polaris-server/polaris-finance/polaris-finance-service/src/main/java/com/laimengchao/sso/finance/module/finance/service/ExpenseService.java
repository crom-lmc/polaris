package com.laimengchao.sso.finance.module.finance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import java.time.LocalDate;

public interface ExpenseService {

    R.Page<FinanceVOs.ExpenseVO> page(String status, String keyword, String dept, LocalDate from, LocalDate to, int page, int size);

    /**
     * 发起报销的默认申请人/归属部门：服务端按当前账号从 EHR 反查。
     * 前端不再用 accountId 本地匹配员工，规避 19 位雪花 ID 在浏览器丢精度导致部门为空。
     */
    FinanceVOs.ExpenseProfileVO myProfile();

    FinanceVOs.ExpenseVO detail(Long id);

    FinanceVOs.ExpenseVO create(FinanceRequests.ExpenseSave req);

    FinanceVOs.ExpenseVO update(Long id, FinanceRequests.ExpenseSave req);

    /**
     * 提交：草稿 / 已驳回 → 待审批。
     * 金额达到埋点 finance.expense.approval-amount 时走统一审批引擎（双 bizType 模板分流），
     * 未达阈值维持原有简单审批（PENDING 后由有权限者直接 approve）。
     */
    void submit(Long id);

    /**
     * 审批通过：待审批 → 已通过；
     * 若埋点 finance.voucher.auto-post=true，则直接生成凭证并过账（POSTED）。
     */
    void approve(Long id);

    /**
     * 审批中心终审回调：通过 → APPROVED（若自动过账埋点开启则直接过账），驳回/撤回/终止 → REJECTED。
     * 幂等：仅 PENDING 状态才更新，重复投递返回 false 由 Controller 以 200 吃掉。
     */
    boolean handleApprovalResult(String bizId, Boolean approved, Integer result);

    /** 驳回：待审批 → 已驳回 */
    void reject(Long id);

    /** 过账：已通过 → 生成凭证并 POSTED */
    void post(Long id);

    /** 付款：已入账 → 已付款 */
    void pay(Long id);

    void delete(Long id);
}
