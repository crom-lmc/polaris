package com.laimengchao.sso.finance.module.finance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import java.util.List;
import java.util.Map;

public interface BudgetSubjectService {

    R.Page<FinanceVOs.BudgetSubjectVO> page(String keyword, Integer year, String status, int page, int size);

    List<FinanceVOs.BudgetSubjectVO> enabledOptions(Integer year);

    /**
     * 服务间只读：启用中的预算科目精简字典（code/name/year），不套数据范围。
     * 供合同域经 internal 令牌读取主数据；主数据挂集团根，对全员可见。
     */
    List<Map<String, Object>> enabledOptionsForInternal(Integer year);

    FinanceVOs.BudgetSubjectVO detail(Long id);

    FinanceVOs.BudgetSubjectVO create(FinanceRequests.BudgetSubjectSave req);

    FinanceVOs.BudgetSubjectVO update(Long id, FinanceRequests.BudgetSubjectSave req);

    void toggle(Long id, String status);

    void delete(Long id);
}
