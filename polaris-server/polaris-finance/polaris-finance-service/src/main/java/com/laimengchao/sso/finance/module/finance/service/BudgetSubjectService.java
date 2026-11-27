package com.laimengchao.sso.finance.module.finance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import java.util.List;

public interface BudgetSubjectService {

    R.Page<FinanceVOs.BudgetSubjectVO> page(String keyword, Integer year, String status, int page, int size);

    List<FinanceVOs.BudgetSubjectVO> enabledOptions(Integer year);

    FinanceVOs.BudgetSubjectVO detail(Long id);

    FinanceVOs.BudgetSubjectVO create(FinanceRequests.BudgetSubjectSave req);

    FinanceVOs.BudgetSubjectVO update(Long id, FinanceRequests.BudgetSubjectSave req);

    void toggle(Long id, String status);

    void delete(Long id);
}
