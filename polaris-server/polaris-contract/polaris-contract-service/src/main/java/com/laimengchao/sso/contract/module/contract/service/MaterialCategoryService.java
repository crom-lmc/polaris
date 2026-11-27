package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterialCategory;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;

import java.util.List;

public interface MaterialCategoryService {

    /** 三级分类树（树里只有分类，不挂物料） */
    List<ContractVOs.MaterialCategoryVO> tree();

    /** 平铺分类列表 */
    List<ContractVOs.MaterialCategoryVO> list();

    /** 指定父分类下的子分类 */
    List<ContractVOs.MaterialCategoryVO> children(Long parentId);

    ContractVOs.MaterialCategoryVO detail(Long id);

    ContractMaterialCategory require(Long id);

    /** 按分类编码取三级分类 */
    ContractMaterialCategory requireByCode(String code);

    ContractVOs.MaterialCategoryVO save(ContractRequests.MaterialCategorySave req);

    void update(Long id, ContractRequests.MaterialCategorySave req);

    void delete(Long id);

    ContractVOs.MaterialCategoryVO toVO(ContractMaterialCategory entity);
}
