package com.laimengchao.sso.finance.module.finance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.service.VoucherService;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import com.laimengchao.sso.finance.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 会计凭证：列表按来源追溯、手工新增与过账。 */
@RestController
@RequestMapping("/api/finance/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @GetMapping
    @Permit(PermissionCodes.FINANCE_VOUCHER_LIST)
    public R<R.Page<FinanceVOs.VoucherVO>> list(
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "sourceNo", required = false) String sourceNo,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(voucherService.page(sourceType, sourceNo, status, keyword, from, to, page, size));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_VOUCHER_LIST)
    public R<FinanceVOs.VoucherVO> detail(@PathVariable("id") Long id) {
        return R.ok(voucherService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.FINANCE_VOUCHER_EDIT)
    public R<FinanceVOs.VoucherVO> create(@RequestBody FinanceRequests.VoucherSave req) {
        return R.ok(voucherService.createManual(req));
    }

    @PostMapping("/{id}/submit-review")
    @Permit(PermissionCodes.FINANCE_VOUCHER_EDIT)
    public R<Void> submitReview(@PathVariable("id") Long id) {
        voucherService.submitReview(id);
        return R.ok();
    }

    @PostMapping("/{id}/review")
    @Permit(PermissionCodes.FINANCE_VOUCHER_EDIT)
    public R<Void> review(@PathVariable("id") Long id) {
        voucherService.review(id);
        return R.ok();
    }

    @PostMapping("/{id}/post")
    @Permit(PermissionCodes.FINANCE_VOUCHER_EDIT)
    public R<Void> post(@PathVariable("id") Long id) {
        voucherService.post(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_VOUCHER_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        voucherService.delete(id);
        return R.ok();
    }
}
