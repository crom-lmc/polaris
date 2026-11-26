package com.laimengchao.sso.contract.client.api;

import com.laimengchao.sso.contract.client.config.ContractFeignConfig;
import com.laimengchao.sso.contract.client.dto.AcceptanceDTO;
import com.laimengchao.sso.contract.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 验收单内部接口，直连 contract-service，不经网关。
 */
@FeignClient(
        name = "contract",
        contextId = "contract-acceptance-client",
        url = "${contract.client.base-url:${CONTRACT_URL:http://127.0.0.1:9015}}",
        configuration = ContractFeignConfig.class)
public interface AcceptanceApi {

    /** 按验收单号查询；无数据时 data 为 null */
    @GetMapping("/internal/contract/acceptances/{no}")
    R<AcceptanceDTO> getByNo(@PathVariable("no") String no);

    /** 验收单过账（幂等） */
    @PostMapping("/internal/contract/acceptances/{no}/posted")
    R<Void> posted(@PathVariable("no") String no);
}
