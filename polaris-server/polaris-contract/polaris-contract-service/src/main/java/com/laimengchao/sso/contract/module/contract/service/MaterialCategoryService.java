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

    /** 指定分类下的全部子孙分类（含二级、三级，不含自身）；parentId 为空时返回一级分类 */
    List<ContractVOs.MaterialCategoryVO> children(Long parentId);

    /** 只返回指定父节点的直接子分类，供选料弹窗树按需展开；停用分类不返回 */
    List<ContractVOs.MaterialCategoryVO> directChildren(Long parentId);

    ContractVOs.MaterialCategoryVO detail(Long id);

    ContractMaterialCategory require(Long id);

    /** 按分类编码取三级分类 */
    ContractMaterialCategory requireByCode(String code);

    ContractVOs.MaterialCategoryVO save(ContractRequests.MaterialCategorySave req);

    void update(Long id, ContractRequests.MaterialCategorySave req);

    /** 启用/停用分类：ENABLED / DISABLED */
    void changeStatus(Long id, String status);

    void delete(Long id);

    ContractVOs.MaterialCategoryVO toVO(ContractMaterialCategory entity);
}
