package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.entity.ContractHeader;
import com.laimengchao.sso.contract.module.contract.vo.ContractVOs;
import java.time.LocalDate;
import java.util.List;

public interface ContractService {

    /** 列表与 internal 列表共用同一套 wrapper 构造 */
    PageResult<ContractVOs.ContractVO> page(String type, String status, String keyword, String projectNo, String partnerCode, int page, int size);

    /** internal 只读：不分页，返回同一份 VO（字段与 contract-client 对齐） */
    List<ContractVOs.ContractVO> list(String type, String status, String projectNo);

    /**
     * 跨服务下拉候选：internal Feign 无登录上下文，按显式传入的账号解析数据范围。
     * 账号为空或范围为空一律返回空集，不绕过数据权限。
     */
    List<ContractVOs.ContractVO> optionsForAccount(Long accountId);

    ContractVOs.ContractVO detail(Long id);

    ContractVOs.ContractVO getByNo(String no);

    ContractHeader require(Long id);

    ContractHeader requireByNo(String no);

    ContractVOs.ContractVO save(ContractRequests.ContractSave req);

    /** 采购计划转合同复用：直接落库并返回实体，清单由调用方写入；fileIds 逗号分隔 file_id 串，planNo 为来源采购计划号 */
    ContractHeader createForPlan(String type, String name, String partnerCode, String projectNo, String dept, String owner, String paymentTerms, String subject, String orgCode, String fileIds, String planNo, LocalDate signedDate, LocalDate effectiveDate, LocalDate endDate, String note);

    void update(Long id, ContractRequests.ContractSave req);

    /** 合同变更记录查询（按合同号，数据范围收口） */
    List<ContractVOs.ChangeLogVO> changeLogs(Long contractId);

    /**
     * 状态流转。采购合同转 PENDING（提交审批）且清单金额达到埋点 contract.purchase.approval-amount 时，
     * 走统一审批引擎（双 bizType 模板分流）；其余状态或金额维持原样直接流转。
     */
    void changeStatus(Long id, String status);

    /**
     * 审批中心终审回调：通过 → EFFECTIVE，驳回/撤回/终止 → DRAFT（可重新编辑提交）。
     * 幂等：仅 PENDING 才更新，重复投递返回 false。
     */
    boolean handleApprovalResult(String bizId, Boolean approved, Integer result);

    void delete(Long id);

    ContractVOs.ContractStatsVO stats(String type);

    /** 批量派生金额与统计；子表查询同样带数据范围 */
    List<ContractVOs.ContractVO> decorate(List<ContractHeader> headers);

    ContractVOs.ContractVO toVO(ContractHeader e);
}
