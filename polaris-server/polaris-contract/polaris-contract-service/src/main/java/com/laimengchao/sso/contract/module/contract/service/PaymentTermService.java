package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractPaymentTerm;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;

import java.util.List;

/** 合同付款节点：草稿阶段配置，比例合计 100%、必含验收款。 */
public interface PaymentTermService {

    /** 合同付款节点列表（含已申请/可申请金额） */
    List<ContractVOs.PaymentTermVO> list(String contractNo);

    /** 内部使用：按合同取节点实体（供付款申请引用校验），不套 dataScope（调用方已校验合同） */
    List<ContractPaymentTerm> entities(String contractNo);

    /** 批量替换合同付款节点：比例合计 100%、必含 ACCEPT、阶段合法；金额按清单合计 × 比例锁定 */
    List<ContractVOs.PaymentTermVO> save(String contractNo, ContractRequests.PaymentTermSaveBatch req);

    /** 按 ID 取节点（供付款申请校验，带 dataScope） */
    ContractPaymentTerm require(Long id);
}
