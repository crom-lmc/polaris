package com.laimengchao.sso.finance.client.api;

import com.laimengchao.sso.finance.client.config.FinanceFeignConfig;
import com.laimengchao.sso.finance.client.dto.R;
import com.laimengchao.sso.finance.client.dto.ReceivableCreateRequest;
import com.laimengchao.sso.finance.client.dto.ReceivableDTO;
import com.laimengchao.sso.finance.client.dto.WriteOffRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 应收款内部接口，直连 finance-service，不经网关。
 */
@FeignClient(
        name = "finance",
        contextId = "finance-receivable-client",
        url = "${finance.client.base-url:${FINANCE_URL:http://127.0.0.1:9014}}",
        configuration = FinanceFeignConfig.class)
public interface ReceivableApi {

    /** 登记应收款 */
    @PostMapping("/internal/finance/receivables/create")
    R<ReceivableDTO> create(@RequestBody ReceivableCreateRequest request);

    /** 核销应收款 */
    @PostMapping("/internal/finance/receivables/write-off")
    R<Boolean> writeOff(@RequestBody WriteOffRequest request);
}
