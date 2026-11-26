package com.laimengchao.sso.finance.module.finance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.service.BudgetService;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** 预算额度：列表派生 used/rate/预警，详情 / 维护。 */
@RestController
@RequestMapping("/api/finance/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    @Permit(PermissionCodes.FINANCE_BUDGET_VIEW)
    public R<R.Page<FinanceVOs.BudgetVO>> list(
            @RequestParam(value = "dept", required = false) String dept,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(budgetService.page(dept, year, page, size));
    }

    @GetMapping("/available")
    @Permit(PermissionCodes.FINANCE_BUDGET_VIEW)
    public R<BigDecimal> available(@RequestParam("dept") String dept,
                                   @RequestParam(value = "year", required = false) Integer year) {
        return R.ok(budgetService.available(dept, year));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_BUDGET_VIEW)
    public R<FinanceVOs.BudgetVO> detail(@PathVariable("id") Long id) {
        return R.ok(budgetService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.FINANCE_BUDGET_EDIT)
    public R<FinanceVOs.BudgetVO> create(@RequestBody FinanceRequests.BudgetSave req) {
        return R.ok(budgetService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_BUDGET_EDIT)
    public R<FinanceVOs.BudgetVO> update(@PathVariable("id") Long id,
                                         @RequestBody FinanceRequests.BudgetSave req) {
        return R.ok(budgetService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_BUDGET_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        budgetService.delete(id);
        return R.ok();
    }
}
