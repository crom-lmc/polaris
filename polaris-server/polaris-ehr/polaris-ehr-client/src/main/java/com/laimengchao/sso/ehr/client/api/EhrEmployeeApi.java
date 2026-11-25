package com.laimengchao.sso.ehr.client.api;

import com.laimengchao.sso.ehr.client.config.EhrFeignConfig;
import com.laimengchao.sso.ehr.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * EHR 员工维度的服务间 API：当前人城市（会议室管理等服务按城市过滤房间用）。
 */
@FeignClient(
        name = "ehr",
        contextId = "ehr-employee-client",
        url = "${ehr.client.base-url:${EHR_URL:http://127.0.0.1:9006}}",
        configuration = EhrFeignConfig.class)
public interface EhrEmployeeApi {

    /**
     * 按账号 ID 取当前人城市（字典 ehr.job_city 的 value）。
     * <p>员工不存在或未配置城市时 data 为 null，调用方必须判空并按空城市 fail-closed。
     */
    @GetMapping("/internal/ehr/employees/{accountId}/city")
    R<String> city(@PathVariable("accountId") Long accountId);
}
