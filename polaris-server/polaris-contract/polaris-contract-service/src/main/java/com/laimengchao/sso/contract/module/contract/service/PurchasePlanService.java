package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractPurchasePlan;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;

public interface PurchasePlanService {

    PageResult<ContractVOs.PurchasePlanVO> page(String no, String status, String keyword, int page, int size);

    ContractVOs.PurchasePlanVO detail(Long id);

    ContractPurchasePlan require(Long id);

    ContractVOs.PurchasePlanVO save(ContractRequests.PurchasePlanSave req);

    void update(Long id, ContractRequests.PurchasePlanSave req);

    void submit(Long id);

    /** 审批通过：DRAFT / PENDING → APPROVED，仅 APPROVED 允许转合同 */
    void approve(Long id);

    void reject(Long id);

    ContractVOs.ContractVO convert(Long id, ContractRequests.PlanConvertRequest req);

    void delete(Long id);
}
