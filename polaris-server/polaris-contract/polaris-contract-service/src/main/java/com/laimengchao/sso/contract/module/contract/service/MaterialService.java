package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterial;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import java.util.List;

public interface MaterialService {

    /** 平铺列表：内部接口按 materialCode 取用 */
    List<ContractVOs.MaterialVO> list();

    /** 树形列表：管理端物料库 */
    List<ContractVOs.MaterialVO> tree();

    ContractVOs.MaterialVO detail(Long id);

    ContractMaterial require(Long id);

    ContractMaterial requireByCode(String code);

    ContractVOs.MaterialVO save(ContractRequests.MaterialSave req);

    void update(Long id, ContractRequests.MaterialSave req);

    void delete(Long id);

    ContractVOs.MaterialVO toVO(ContractMaterial e);
}
