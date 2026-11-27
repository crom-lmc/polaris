package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterial;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;

import java.util.List;

public interface MaterialService {

    /** 平铺物料列表：内部接口按 materialCode 取用 */
    List<ContractVOs.MaterialVO> list();

    /** 指定三级分类下的物料 */
    List<ContractVOs.MaterialVO> byCategory(Long categoryId);

    /** 分类 + 物料拼合树：仅内部接口兼容使用，管理端已拆为分类树 + 物料列表 */
    List<ContractVOs.MaterialVO> tree();

    ContractVOs.MaterialVO detail(Long id);

    ContractMaterial require(Long id);

    ContractMaterial requireByCode(String code);

    ContractVOs.MaterialVO save(ContractRequests.MaterialSave req);

    void update(Long id, ContractRequests.MaterialSave req);

    void delete(Long id);

    ContractVOs.MaterialVO toVO(ContractMaterial entity);
}
