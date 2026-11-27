package com.laimengchao.sso.contract.client.api;

import com.laimengchao.sso.contract.client.config.ContractFeignConfig;
import com.laimengchao.sso.contract.client.dto.PartnerDTO;
import com.laimengchao.sso.contract.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 往来单位（客户 / 供应商）内部接口，直连 contract-service，不经网关。
 */
@FeignClient(
        name = "contract",
        contextId = "contract-partner-client",
        url = "${contract.client.base-url:${CONTRACT_URL:http://127.0.0.1:9015}}",
        configuration = ContractFeignConfig.class)
public interface PartnerApi {

    /** 按往来单位编码查询；无数据时 data 为 null，调用方必须判空 */
    @GetMapping("/internal/contract/partners/{code}")
    R<PartnerDTO> getByCode(@PathVariable("code") String code);

    /** 按角色 / 状态筛选往来单位；调用方必须判空并按空集兜底 */
    @GetMapping("/internal/contract/partners")
    R<List<PartnerDTO>> list(@RequestParam(value = "role", required = false) String role,
                             @RequestParam(value = "status", required = false) String status);

    /**
     * 下拉候选：显式传账号，被调方按该账号的数据范围过滤（internal Feign 无 X-Account-Id）。
     * role 传 CUSTOMER / SUPPLIER 时同时包含 BOTH 角色。
     */
    @GetMapping("/internal/contract/partner-options")
    R<List<PartnerDTO>> options(@RequestParam(value = "role", required = false) String role,
                                @RequestParam(value = "status", required = false) String status,
                                @RequestParam(value = "accountId", required = false) Long accountId);
}
