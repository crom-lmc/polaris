package com.laimengchao.sso.contract.client.api;

import com.laimengchao.sso.contract.client.config.ContractFeignConfig;
import com.laimengchao.sso.contract.client.dto.ProjectDTO;
import com.laimengchao.sso.contract.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 项目主数据内部接口，直连 contract-service，不经网关。
 */
@FeignClient(
        name = "contract",
        contextId = "contract-project-client",
        url = "${contract.client.base-url:${CONTRACT_URL:http://127.0.0.1:9015}}",
        configuration = ContractFeignConfig.class)
public interface ProjectApi {

    /** 按项目编码查询；无数据时 data 为 null */
    @GetMapping("/internal/contract/projects/{code}")
    R<ProjectDTO> getByCode(@PathVariable("code") String code);

    /** 项目下拉选项（可用项目） */
    @GetMapping("/internal/contract/projects/options")
    R<List<ProjectDTO>> options();

    /** 项目是否可用；令牌缺失或调用失败时必须按 false 处理（fail-closed） */
    @GetMapping("/internal/contract/projects/{code}/usable")
    R<Boolean> usable(@PathVariable("code") String code);
}
