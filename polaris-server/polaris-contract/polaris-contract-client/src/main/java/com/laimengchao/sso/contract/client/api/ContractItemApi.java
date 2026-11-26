package com.laimengchao.sso.contract.client.api;

import com.laimengchao.sso.contract.client.config.ContractFeignConfig;
import com.laimengchao.sso.contract.client.dto.ContractItemDTO;
import com.laimengchao.sso.contract.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 合同明细行内部接口，直连 contract-service，不经网关。
 */
@FeignClient(
        name = "contract",
        contextId = "contract-item-client",
        url = "${contract.client.base-url:${CONTRACT_URL:http://127.0.0.1:9015}}",
        configuration = ContractFeignConfig.class)
public interface ContractItemApi {

    /** 合同下全部明细行；无数据时返回空集合 */
    @GetMapping("/internal/contract/contracts/{no}/items")
    R<List<ContractItemDTO>> items(@PathVariable("no") String no);
}
