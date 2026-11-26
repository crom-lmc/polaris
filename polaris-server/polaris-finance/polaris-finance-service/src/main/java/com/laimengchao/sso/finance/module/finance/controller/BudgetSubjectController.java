package com.laimengchao.sso.finance.module.finance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.service.BudgetSubjectService;
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

import java.util.List;

/** 预算科目主数据管理。 */
@RestController
@RequestMapping("/api/finance/budget-subjects")
@RequiredArgsConstructor
public class BudgetSubjectController {

    private final BudgetSubjectService budgetSubjectService;

    @GetMapping
    @Permit(PermissionCodes.FINANCE_MASTER_LIST)
    public R<R.Page<FinanceVOs.BudgetSubjectVO>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(budgetSubjectService.page(keyword, year, status, page, size));
    }

    @GetMapping("/options")
    @Permit(PermissionCodes.FINANCE_MASTER_LIST)
    public R<List<FinanceVOs.BudgetSubjectVO>> options(
            @RequestParam(value = "year", required = false) Integer year) {
        return R.ok(budgetSubjectService.enabledOptions(year));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_MASTER_LIST)
    public R<FinanceVOs.BudgetSubjectVO> detail(@PathVariable("id") Long id) {
        return R.ok(budgetSubjectService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.FINANCE_MASTER_EDIT)
    public R<FinanceVOs.BudgetSubjectVO> create(@RequestBody FinanceRequests.BudgetSubjectSave req) {
        return R.ok(budgetSubjectService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_MASTER_EDIT)
    public R<FinanceVOs.BudgetSubjectVO> update(@PathVariable("id") Long id,
                                                @RequestBody FinanceRequests.BudgetSubjectSave req) {
        return R.ok(budgetSubjectService.update(id, req));
    }

    @PostMapping("/{id}/toggle")
    @Permit(PermissionCodes.FINANCE_MASTER_EDIT)
    public R<Void> toggle(@PathVariable("id") Long id,
                          @RequestParam(value = "status", required = false) String status) {
        budgetSubjectService.toggle(id, status);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_MASTER_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        budgetSubjectService.delete(id);
        return R.ok();
    }
}
