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
     * 发起付款申请：仅推进状态；sourceDocNo 由财务域建应付/付款单后回写，
     * 合同域不自造 YS/FK 单号。
     */
    ContractVOs.PaymentPlanVO apply(Long id, ContractRequests.PaymentApplyRequest req);

    /** 财务域回写来源单据号、凭证号与状态（internal token 准入） */
    ContractVOs.PaymentPlanVO writeBack(Long id, String sourceDocNo, String voucherNo, String status);

}
