package com.laimengchao.sso.ehr.module.employee.service;

import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeChangeRequest;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeChangeResultVO;

public interface EmployeeChangeService {

    /**
     * 提交信息变更。基本信息只比 7 个可变字段；子表按「目标态整表」与现状逐行比对：
     * 带 id 的行比内容（MOD）、不带 id 的是新增（ADD）、库里有但没提交上来的是删除（DEL）。
     * 什么都没改直接报错，不浪费一张审批单。
     */
    EmployeeChangeResultVO submit(Long id, EmployeeChangeRequest req);

    void handleApprovalResult(ApprovalCallbackRequest req);
}
