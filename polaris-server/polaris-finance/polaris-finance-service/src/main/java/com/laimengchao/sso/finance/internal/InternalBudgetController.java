package com.laimengchao.sso.finance.internal;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 合同域预算能力：可用额度查询、合同承诺占用/释放、已付/在途回写。
 * 无 @Permit，由 common 的 InternalTokenInterceptor 统一把关。
 */
@RestController
@RequestMapping("/internal/finance/budgets")
@RequiredArgsConstructor
public class InternalBudgetController {

    private final BudgetService budgetService;

    /** 可用额度：无预算记录返回 0（读接口兜底） */
    @GetMapping("/available")
    public R<BigDecimal> available(@RequestParam("dept") String dept,
                                   @RequestParam(value = "year", required = false) Integer year) {
        return R.ok(budgetService.available(dept, year));
    }

    /** 合同承诺占用：超额 fail-closed 抛业务错误 */
    @PostMapping("/occupy")
    public R<Void> occupy(@RequestBody FinanceRequests.BudgetOperateRequest req) {
        budgetService.occupy(req.getDept(), req.getYear(), req.getAmount());
        return R.ok();
    }

    /** 合同承诺释放 */
    @PostMapping("/release")
    public R<Void> release(@RequestBody FinanceRequests.BudgetOperateRequest req) {
        budgetService.release(req.getDept(), req.getYear(), req.getAmount());
        return R.ok();
    }

    /** 合同域回写已付/在途金额 */
    @PostMapping("/contract-stats")
    public R<Void> contractStats(@RequestBody FinanceRequests.BudgetOperateRequest req) {
        budgetService.syncContractStats(req.getDept(), req.getYear(), req.getAmount(), req.getInFlightAmount());
        return R.ok();
    }
}
