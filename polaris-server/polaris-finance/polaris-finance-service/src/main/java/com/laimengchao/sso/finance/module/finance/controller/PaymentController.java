package com.laimengchao.sso.finance.module.finance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.service.PaymentService;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 付款单：合同付款申请生成的单据查询与付款动作。 */
@RestController
@RequestMapping("/api/finance/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @Permit(PermissionCodes.FINANCE_AR_AP_LIST)
    public R<R.Page<FinanceVOs.PaymentVO>> list(
            @RequestParam(value = "contractNo", required = false) String contractNo,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(paymentService.page(contractNo, status, keyword, page, size));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_AR_AP_LIST)
    public R<FinanceVOs.PaymentVO> detail(@PathVariable("id") Long id) {
        return R.ok(paymentService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.FINANCE_AR_AP_WRITE)
    public R<FinanceVOs.PaymentVO> create(@RequestBody FinanceRequests.PaymentSave req) {
        return R.ok(paymentService.create(req));
    }

    @PostMapping("/{id}/pay")
    @Permit(PermissionCodes.FINANCE_AR_AP_WRITE)
    public R<Void> pay(@PathVariable("id") Long id) {
        paymentService.pay(id);
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    @Permit(PermissionCodes.FINANCE_AR_AP_WRITE)
    public R<Void> cancel(@PathVariable("id") Long id) {
        paymentService.cancel(id);
        return R.ok();
    }
}
