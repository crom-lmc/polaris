package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractPurchasePlan;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;

public interface PurchasePlanService {

    PageResult<ContractVOs.PurchasePlanVO> page(String no, String status, String keyword, int page, int size);

    ContractVOs.PurchasePlanVO detail(Long id);

    /** 发起采购计划的默认申请人/需求部门：服务端按账号 ID 从 EHR 反查 */
    ContractVOs.PurchasePlanProfileVO myProfile();

    ContractPurchasePlan require(Long id);

    ContractVOs.PurchasePlanVO save(ContractRequests.PurchasePlanSave req);

    void update(Long id, ContractRequests.PurchasePlanSave req);

    /** 提交审批：PENDING + 发起审批中心实例，终审回调推进 APPROVED / REJECTED */
    void submit(Long id);

    /** 审批通过：DRAFT / PENDING → APPROVED，仅 APPROVED 允许转合同（存量无实例单据可用） */
    void approve(Long id);

    void reject(Long id);

    /** 审批中心终审回调：PENDING→APPROVED/REJECTED（幂等） */
    boolean handleApprovalResult(String bizId, Boolean approved, Integer result);

    /** 审批信息只读查询（查无实例返回 null） */
    ContractVOs.ApprovalInfoVO approvalInfo(Long id);

    ContractVOs.ContractVO convert(Long id, ContractRequests.PlanConvertRequest req);

    void delete(Long id);
}
