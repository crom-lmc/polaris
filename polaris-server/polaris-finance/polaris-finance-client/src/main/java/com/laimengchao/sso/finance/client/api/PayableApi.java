package com.laimengchao.sso.finance.client.api;

import com.laimengchao.sso.finance.client.config.FinanceFeignConfig;
import com.laimengchao.sso.finance.client.dto.PayableCreateRequest;
import com.laimengchao.sso.finance.client.dto.PayableDTO;
import com.laimengchao.sso.finance.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 应付款内部接口，直连 finance-service，不经网关。
 */
@FeignClient(
        name = "finance",
        contextId = "finance-payable-client",
        url = "${finance.client.base-url:${FINANCE_URL:http://127.0.0.1:9014}}",
        configuration = FinanceFeignConfig.class)
public interface PayableApi {

    /** 合同下全部应付款；调用方必须判空并按空集兜底 */
    @GetMapping("/internal/finance/payables/by-contract/{contractNo}")
    R<List<PayableDTO>> byContract(@PathVariable("contractNo") String contractNo);

    /** 登记应付款 */
    @PostMapping("/internal/finance/payables/create")
    R<PayableDTO> create(@RequestBody PayableCreateRequest request);
}
