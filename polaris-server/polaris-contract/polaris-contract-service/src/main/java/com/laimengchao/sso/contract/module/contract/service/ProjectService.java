package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractProject;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import java.util.List;

public interface ProjectService {

    PageResult<ContractVOs.ProjectVO> page(String code, String name, String type, String status, int page, int size);

    ContractVOs.ProjectVO detail(Long id);

    List<ContractVOs.ProjectOptionVO> options();

    /** 供合同侧与 internal 接口调用：项目是否允许新关联单据 */
    ContractVOs.ProjectUsableVO validateUsable(String code);

    /** 合同侧强校验：不可用直接抛业务错误 */
    ContractProject requireUsable(String code);

    ContractProject require(Long id);

    ContractVOs.ProjectVO save(ContractRequests.ProjectSave req);

    void update(Long id, ContractRequests.ProjectSave req);

    void changeStatus(Long id, String status);

    void delete(Long id);

    /** 提交项目审批：DRAFT→PENDING 并进审批中心，通过/驳回由回调推进 */
    void submit(Long id);

    /** 审批中心终审回调：PENDING→ACTIVE（通过）/DRAFT（驳回），幂等 */
    boolean handleApprovalResult(String bizId, Boolean approved, Integer result);

    /** 项目审批信息：按 bizType+bizId 反查审批实例 ID */
    ContractVOs.ApprovalInfoVO approvalInfo(Long id);
}
