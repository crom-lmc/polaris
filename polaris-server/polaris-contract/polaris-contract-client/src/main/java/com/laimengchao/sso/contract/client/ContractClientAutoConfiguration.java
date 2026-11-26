package com.laimengchao.sso.contract.client;

import com.laimengchao.sso.contract.client.api.AcceptanceApi;
import com.laimengchao.sso.contract.client.api.ContractApi;
import com.laimengchao.sso.contract.client.api.ContractItemApi;
import com.laimengchao.sso.contract.client.api.MaterialApi;
import com.laimengchao.sso.contract.client.api.PartnerApi;
import com.laimengchao.sso.contract.client.api.ProjectApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 引入 polaris-contract-client 即自动装配合同域服务间 Feign 客户端。
 * 地址键：{@code contract.client.base-url}；令牌键：{@code contract.client.internal-token}（缺省复用 {@code SSO_INTERNAL_TOKEN}）。
 */
@AutoConfiguration
@EnableFeignClients(clients = {
        PartnerApi.class,
        MaterialApi.class,
        ContractApi.class,
        ContractItemApi.class,
        ProjectApi.class,
        AcceptanceApi.class
})
public class ContractClientAutoConfiguration {
}
