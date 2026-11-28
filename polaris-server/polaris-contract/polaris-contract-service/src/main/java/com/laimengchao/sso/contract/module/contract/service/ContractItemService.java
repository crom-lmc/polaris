package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractAcceptance;
import com.laimengchao.sso.contract.module.contract.entity.ContractItem;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import java.util.List;

public interface ContractItemService {

    List<ContractVOs.ItemVO> list(String contractNo);

    ContractVOs.ItemVO detail(Long id);

    ContractItem require(Long id);

    ContractVOs.ItemVO save(String contractNo, ContractRequests.ItemSave req);

    void delete(Long id);

    /** 到货验收：增加 deliveredQty，并生成 JD 验收单（单号走 settings） */
    ContractVOs.AcceptanceVO accept(Long id, ContractRequests.AcceptRequest req);

    /** 到货签收：增加 signedQty，并生成 QS 签收单（到货款放行依据，与验收独立） */
    ContractVOs.SignVO sign(Long id, ContractRequests.SignRequest req);

    /** 签收记录列表 */
    List<ContractVOs.SignVO> signs(String contractNo);

    /** 销售验收重试推应收：验收已落库但推应收失败时可重试（幂等，已有 arNo 直接返回） */
    void pushReceivable(Long acceptanceId);

    /** 销售合同应收全部核销完成 + 清单全部验收完成 → EXECUTING→COMPLETED（幂等；财务核销回调用） */
    void tryCompleteOnArSettled(String contractNo);

    List<ContractVOs.AcceptanceVO> acceptances(String contractNo);

    ContractAcceptance requireAcceptance(String no);

    /** 财务域过账后回写验收单状态 */
    ContractVOs.AcceptanceVO markPosted(String no);

    /** 财务域过账后回写验收单状态与凭证号 */
    ContractVOs.AcceptanceVO markPosted(String no, String voucherNo);
}
