package com.laimengchao.sso.finance.module.finance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import java.util.List;

public interface SubjectService {

    /** 主数据列表：数据范围过滤；status/level/category 可筛选 */
    R.Page<FinanceVOs.SubjectVO> page(String keyword, String status, Integer level, String category, int page, int size);

    /** 下拉：仅启用科目，仍按数据范围过滤 */
    List<FinanceVOs.SubjectVO> enabledOptions();

    FinanceVOs.SubjectVO detail(Long id);

    FinanceVOs.SubjectVO create(FinanceRequests.SubjectSave req);

    FinanceVOs.SubjectVO update(Long id, FinanceRequests.SubjectSave req);

    /** 启停：ENABLED ↔ DISABLED */
    void toggle(Long id, String status);

    /** 删除：被凭证（subject 存编码或名称）或预算科目引用时禁止 */
    void delete(Long id);
}
