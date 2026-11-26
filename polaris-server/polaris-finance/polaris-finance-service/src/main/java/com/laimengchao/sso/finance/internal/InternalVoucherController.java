package com.laimengchao.sso.finance.internal;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.FinanceRequests;
import com.laimengchao.sso.finance.module.finance.service.VoucherService;
import com.laimengchao.sso.finance.module.finance.vo.FinanceVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合同域凭证能力：按业务单号查询（来源追溯）、过账回写生成凭证。
 * 无 @Permit，由 common 的 InternalTokenInterceptor 统一把关。
 */
@RestController
@RequestMapping("/internal/finance/vouchers")
@RequiredArgsConstructor
public class InternalVoucherController {

    private final VoucherService voucherService;

    /**
     * 按业务单号/来源查凭证，无数据返回空集。
     * 两个路径并存：/by-biz-no 供管理端追溯，/by-biz 是 polaris-finance-client/VoucherApi 契约。
     */
    @GetMapping({ "/by-biz-no", "/by-biz" })
    public R<List<FinanceVOs.VoucherVO>> byBizNo(
            @RequestParam(name = "no", required = false) String no,
            @RequestParam(name = "sourceNo", required = false) String sourceNo,
            @RequestParam(name = "sourceType", required = false) String sourceType) {
        return R.ok(voucherService.bySourceNo(no != null && !no.isBlank() ? no : sourceNo));
    }

    /** 业务单过账：同来源+单号幂等；/post 为 client 契约路径 */
    @PostMapping({ "/post-from-biz", "/post" })
    public R<FinanceVOs.VoucherVO> postFromBiz(@RequestBody FinanceRequests.VoucherPostRequest req) {
        return R.ok(voucherService.postFromBiz(req));
    }
}
