package com.laimengchao.sso.ehr.module.leave.service;

import com.laimengchao.sso.ehr.module.leave.entity.LeaveTypeRule;
import java.util.List;

public interface LeaveTypeRuleService {

    List<LeaveTypeRule> list();

    /** 规则缺失时按「允许申请、不占额度、0.5~30 天」兜底，保证老字典类型还能用 */
    LeaveTypeRule get(String type);

    /** 需要占用额度的类型（首次建额度也要用默认额度） */
    boolean quotaRequired(String type);

    LeaveTypeRule save(LeaveTypeRule rule);
}
