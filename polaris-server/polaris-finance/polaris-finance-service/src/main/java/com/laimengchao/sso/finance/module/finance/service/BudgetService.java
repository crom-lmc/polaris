package com.laimengchao.sso.finance.module.finance.service;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import java.math.BigDecimal;
import java.util.List;

public interface BudgetService {

    /** 列表：used/current 由已过账凭证按部门与年度派生，执行率按埋点预警 */
    R.Page<FinanceVOs.BudgetVO> page(String dept, Integer year, int page, int size);

    List<FinanceVOs.BudgetVO> listAll(Integer year);

    FinanceVOs.BudgetVO detail(Long id);

    FinanceVOs.BudgetVO create(FinanceRequests.BudgetSave req);

    FinanceVOs.BudgetVO update(Long id, FinanceRequests.BudgetSave req);

    void delete(Long id);

    /** 合同域查询可用额度：无预算返回 0（读接口 fail-closed） */
    BigDecimal available(String dept, Integer year);

    /** 合同承诺占用：累加 contract_committed，超额抛业务错误 */
    void occupy(String dept, Integer year, BigDecimal amount);

    /** 合同承诺释放：扣减 contract_committed，最低 0 */
    void release(String dept, Integer year, BigDecimal amount);

    /** 合同域写入已付/在途（只更新这两个字段，不碰 base 与占用） */
    void syncContractStats(String dept, Integer year, BigDecimal paid, BigDecimal inFlight);
}
