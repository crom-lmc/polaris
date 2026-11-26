package com.laimengchao.sso.finance.client.api;

import com.laimengchao.sso.finance.client.config.FinanceFeignConfig;
import com.laimengchao.sso.finance.client.dto.R;
import com.laimengchao.sso.finance.client.dto.VoucherDTO;
import com.laimengchao.sso.finance.client.dto.VoucherPostRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 记账凭证内部接口，直连 finance-service，不经网关。
 */
@FeignClient(
        name = "finance",
        contextId = "finance-voucher-client",
        url = "${finance.client.base-url:${FINANCE_URL:http://127.0.0.1:9014}}",
        configuration = FinanceFeignConfig.class)
public interface VoucherApi {

    /** 按业务来源查凭证；无数据时 data 为 null */
    @GetMapping("/internal/finance/vouchers/by-biz")
    R<List<VoucherDTO>> getByBiz(@RequestParam("sourceType") String sourceType,
                           @RequestParam("sourceNo") String sourceNo);

    /** 凭证过账（按 sourceType + sourceNo 幂等） */
    @PostMapping("/internal/finance/vouchers/post")
    R<VoucherDTO> post(@RequestBody VoucherPostRequest request);
}
