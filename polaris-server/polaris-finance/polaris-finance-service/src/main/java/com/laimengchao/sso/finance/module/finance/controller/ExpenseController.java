package com.laimengchao.sso.finance.module.finance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.service.ExpenseService;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 费用报销单：创建、提交、审批、驳回、过账、付款。 */
@RestController
@RequestMapping("/api/finance/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    @Permit(PermissionCodes.FINANCE_EXPENSE_LIST)
    public R<R.Page<FinanceVOs.ExpenseVO>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "dept", required = false) String dept,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(expenseService.page(status, keyword, dept, from, to, page, size));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_EXPENSE_LIST)
    public R<FinanceVOs.ExpenseVO> detail(@PathVariable("id") Long id) {
        return R.ok(expenseService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.FINANCE_EXPENSE_CREATE)
    public R<FinanceVOs.ExpenseVO> create(@RequestBody FinanceRequests.ExpenseSave req) {
        return R.ok(expenseService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_EXPENSE_CREATE)
    public R<FinanceVOs.ExpenseVO> update(@PathVariable("id") Long id,
                                          @RequestBody FinanceRequests.ExpenseSave req) {
        return R.ok(expenseService.update(id, req));
    }

    @PostMapping("/{id}/submit")
    @Permit(PermissionCodes.FINANCE_EXPENSE_CREATE)
    public R<Void> submit(@PathVariable("id") Long id) {
        expenseService.submit(id);
        return R.ok();
    }

    @PostMapping("/{id}/approve")
    @Permit(PermissionCodes.FINANCE_EXPENSE_AUDIT)
    public R<Void> approve(@PathVariable("id") Long id) {
        expenseService.approve(id);
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    @Permit(PermissionCodes.FINANCE_EXPENSE_AUDIT)
    public R<Void> reject(@PathVariable("id") Long id) {
        expenseService.reject(id);
        return R.ok();
    }

    @PostMapping("/{id}/post")
    @Permit(PermissionCodes.FINANCE_EXPENSE_AUDIT)
    public R<Void> post(@PathVariable("id") Long id) {
        expenseService.post(id);
        return R.ok();
    }

    @PostMapping("/{id}/pay")
    @Permit(PermissionCodes.FINANCE_EXPENSE_AUDIT)
    public R<Void> pay(@PathVariable("id") Long id) {
        expenseService.pay(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_EXPENSE_CREATE)
    public R<Void> delete(@PathVariable("id") Long id) {
        expenseService.delete(id);
        return R.ok();
    }
}
