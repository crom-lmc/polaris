package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractPartner;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import java.util.List;

public interface PartnerService {

    PageResult<ContractVOs.PartnerVO> page(String code, String name, String role, String status, String keyword, int page, int size);

    /** 下拉 / 跨服务只读：返回全局可见的合作中单位，但同样按数据范围收口 */
    List<ContractVOs.PartnerVO> list(String role, String status);

    /** internal Feign 下拉：按显式账号解析数据范围，role 匹配时同时包含 BOTH */
    List<ContractVOs.PartnerVO> optionsForAccount(String role, String status, Long accountId);

    ContractVOs.PartnerVO detail(Long id);

    ContractPartner require(Long id);

    ContractPartner requireByCode(String code);

    ContractVOs.PartnerVO save(ContractRequests.PartnerSave req);

    void update(Long id, ContractRequests.PartnerSave req);

    void toggle(Long id, String status);

    void delete(Long id);

    /** 银行账号与个人证件号仅在有明文权限时展示，否则脱敏 */
    ContractVOs.PartnerVO toVO(ContractPartner e, boolean bankVisible);
}
