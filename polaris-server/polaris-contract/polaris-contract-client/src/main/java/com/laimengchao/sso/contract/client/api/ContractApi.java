package com.laimengchao.sso.contract.client.api;

import com.laimengchao.sso.contract.client.config.ContractFeignConfig;
import com.laimengchao.sso.contract.client.dto.ContractDTO;
import com.laimengchao.sso.contract.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 合同台账内部接口，直连 contract-service，不经网关。
 * <p>金额类字段供财务域做预算占用 / 付款在途校验，调用方必须显式判空解包。
 */
@FeignClient(
        name = "contract",
        contextId = "contract-contract-client",
        url = "${contract.client.base-url:${CONTRACT_URL:http://127.0.0.1:9015}}",
        configuration = ContractFeignConfig.class)
public interface ContractApi {

    /** 按合同编号查询；无数据时 data 为 null */
    @GetMapping("/internal/contract/contracts/{no}")
    R<ContractDTO> getByNo(@PathVariable("no") String no);

    /** 按类型 / 状态 / 所属项目筛选合同 */
    @GetMapping("/internal/contract/contracts")
    R<List<ContractDTO>> list(@RequestParam(value = "type", required = false) String type,
                              @RequestParam(value = "status", required = false) String status,
                              @RequestParam(value = "projectNo", required = false) String projectNo);
}
