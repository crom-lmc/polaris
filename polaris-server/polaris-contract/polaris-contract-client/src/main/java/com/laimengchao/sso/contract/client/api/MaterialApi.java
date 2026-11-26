package com.laimengchao.sso.contract.client.api;

import com.laimengchao.sso.contract.client.config.ContractFeignConfig;
import com.laimengchao.sso.contract.client.dto.MaterialDTO;
import com.laimengchao.sso.contract.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 物料 / 服务目录内部接口，直连 contract-service，不经网关。
 */
@FeignClient(
        name = "contract",
        contextId = "contract-material-client",
        url = "${contract.client.base-url:${CONTRACT_URL:http://127.0.0.1:9015}}",
        configuration = ContractFeignConfig.class)
public interface MaterialApi {

    /** 物料目录树（平铺返回，含层级编码） */
    @GetMapping("/internal/contract/materials/tree")
    R<List<MaterialDTO>> tree();

    /** 按物料编码查询；无数据时 data 为 null，调用方必须判空 */
    @GetMapping("/internal/contract/materials/{code}")
    R<MaterialDTO> getByCode(@PathVariable("code") String code);
}
