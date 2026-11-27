package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractPaymentPlan;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import java.util.List;

public interface PaymentPlanService {

    List<ContractVOs.PaymentPlanVO> list(String contractNo);

    ContractVOs.PaymentPlanVO detail(Long id);

    ContractPaymentPlan require(Long id);

    ContractVOs.PaymentPlanVO save(String contractNo, ContractRequests.PaymentPlanSave req);

    void update(Long id, ContractRequests.PaymentPlanSave req);

    void delete(Long id);

    /**
     * 发起付款申请：置 PENDING 并进审批中心；终审通过后由回调推进 APPROVED。
     * sourceDocNo 由财务域建应付/付款单后回写，合同域不自造 YS/FK 单号。
     */
    ContractVOs.PaymentPlanVO apply(Long id, ContractRequests.PaymentApplyRequest req);

    /** 审批中心终审回调：通过 PENDING→APPROVED，驳回/撤回/终止 PENDING→REJECTED（幂等） */
    boolean handleApprovalResult(String bizId, Boolean approved, Integer result);

    /** 付款计划发起审批后的只读审批信息（复用 settings 审批中心；未发起返回 null） */
    ContractVOs.ApprovalInfoVO approvalInfo(Long id);

    /** 财务域回写来源单据号、凭证号与状态（internal token 准入） */
    ContractVOs.PaymentPlanVO writeBack(Long id, String sourceDocNo, String voucherNo, String status);

}
