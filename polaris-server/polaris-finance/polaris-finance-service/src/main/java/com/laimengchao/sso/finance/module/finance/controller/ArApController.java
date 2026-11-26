package com.laimengchao.sso.finance.module.finance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.service.ArApService;
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
import java.util.List;

/** 应收应付：过账、核销与销售验收推应收。 */
@RestController
@RequestMapping("/api/finance/ar-ap")
@RequiredArgsConstructor
public class ArApController {

    private final ArApService arApService;

    @GetMapping
    @Permit(PermissionCodes.FINANCE_AR_AP_LIST)
    public R<R.Page<FinanceVOs.ArApVO>> list(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "contractNo", required = false) String contractNo,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(arApService.page(type, status, keyword, contractNo, from, to, page, size));
    }

    /** 账龄分档：区间由埋点 finance.aging.buckets 决定 */
    @GetMapping("/aging")
    @Permit(PermissionCodes.FINANCE_AR_AP_LIST)
    public R<List<FinanceVOs.AgingBucketVO>> aging() {
        return R.ok(arApService.aging());
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_AR_AP_LIST)
    public R<FinanceVOs.ArApVO> detail(@PathVariable("id") Long id) {
        return R.ok(arApService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.FINANCE_AR_AP_WRITE)
    public R<FinanceVOs.ArApVO> create(@RequestBody FinanceRequests.ArApSave req) {
        return R.ok(arApService.create(req));
    }

    @PostMapping("/{id}/approve")
    @Permit(PermissionCodes.FINANCE_AR_AP_WRITE)
    public R<Void> approve(@PathVariable("id") Long id) {
        arApService.approve(id);
        return R.ok();
    }

    @PostMapping("/{id}/post")
    @Permit(PermissionCodes.FINANCE_AR_AP_WRITE)
    public R<Void> post(@PathVariable("id") Long id) {
        arApService.post(id);
        return R.ok();
    }

    @PostMapping("/{id}/write-off")
    @Permit(PermissionCodes.FINANCE_AR_AP_WRITE)
    public R<Void> writeOff(@PathVariable("id") Long id,
                            @RequestBody FinanceRequests.WriteOffRequest req) {
        arApService.writeOff(id, req);
        return R.ok();
    }

    /** 销售合同验收推应收（管理端手动触发，合同域也可走 internal 接口） */
    @PostMapping("/from-acceptance")
    @Permit(PermissionCodes.FINANCE_AR_AP_WRITE)
    public R<FinanceVOs.ArApVO> createFromAcceptance(@RequestBody FinanceRequests.AcceptanceArRequest req) {
        return R.ok(arApService.createFromAcceptance(req));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.FINANCE_AR_AP_WRITE)
    public R<Void> delete(@PathVariable("id") Long id) {
        arApService.delete(id);
        return R.ok();
    }
}
