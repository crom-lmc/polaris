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

    List<ContractVOs.AcceptanceVO> acceptances(String contractNo);

    ContractAcceptance requireAcceptance(String no);

    /** 财务域过账后回写验收单状态 */
    ContractVOs.AcceptanceVO markPosted(String no);

    /** 财务域过账后回写验收单状态与凭证号 */
    ContractVOs.AcceptanceVO markPosted(String no, String voucherNo);
}
