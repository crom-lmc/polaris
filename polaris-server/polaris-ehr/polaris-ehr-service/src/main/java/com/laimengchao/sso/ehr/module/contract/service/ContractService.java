package com.laimengchao.sso.ehr.module.contract.service;

import com.laimengchao.sso.ehr.module.contract.dto.ContractRenewRequest;
import com.laimengchao.sso.ehr.module.contract.dto.ContractRequest;
import com.laimengchao.sso.ehr.module.contract.dto.ContractTerminateRequest;
import com.laimengchao.sso.ehr.module.contract.vo.ContractVO;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import java.util.List;

public interface ContractService {

    List<ContractVO> listByEmployee(Long employeeId);

    ContractVO create(Long employeeId, ContractRequest req);

    ContractVO update(Long contractId, ContractRequest req);

    ContractVO renew(Long contractId, ContractRenewRequest req);

    ContractVO terminate(Long contractId, ContractTerminateRequest req);

    void handleApprovalResult(ApprovalCallbackRequest req);

    /** 把已过结束日期的生效合同置为 EXPIRED；每日提醒任务与合同列表都会调用 */
    int refreshExpired();

    String typeName(String type);

    String statusName(String status);
}
