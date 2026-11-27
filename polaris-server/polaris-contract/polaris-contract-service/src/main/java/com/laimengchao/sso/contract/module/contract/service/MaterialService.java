package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractMaterial;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;

import java.util.List;

public interface MaterialService {

    /** 平铺物料列表：内部接口按 materialCode 取用 */
    List<ContractVOs.MaterialVO> list();

    /** 指定分类及其全部子孙分类下的物料；categoryId 为空时返回未挂分类的物料 */
    List<ContractVOs.MaterialVO> byCategory(Long categoryId);

    /** 分类 + 物料拼合树：仅内部接口兼容使用，管理端已拆为分类树 + 物料列表 */
    List<ContractVOs.MaterialVO> tree();

    /** 选料弹窗的分页搜索：按分类（含子孙）与关键字过滤，停用分类下物料不可选 */
    PageResult<ContractVOs.MaterialVO> search(String keyword, Long categoryId, int page, int size);

    ContractVOs.MaterialVO detail(Long id);

    ContractMaterial require(Long id);

    ContractMaterial requireByCode(String code);

    ContractVOs.MaterialVO save(ContractRequests.MaterialSave req);

    void update(Long id, ContractRequests.MaterialSave req);

    void delete(Long id);

    ContractVOs.MaterialVO toVO(ContractMaterial entity);
}
