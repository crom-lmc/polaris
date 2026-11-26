package com.laimengchao.sso.ehr.module.leave.service;

import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.leave.dto.QuotaAdjustRequest;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuotaAdjust;

public interface LeaveQuotaAdjustService {

    LeaveQuotaAdjust apply(Long quotaId, QuotaAdjustRequest req);

    void handleResult(ApprovalCallbackRequest req);
}
