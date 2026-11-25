package com.laimengchao.sso.ehr.client;

import com.laimengchao.sso.ehr.client.api.EhrEmployeeApi;
import com.laimengchao.sso.ehr.client.api.EhrOrgApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 引入 polaris-ehr-client 即自动装配 EHR 服务间 Feign 客户端。
 * 地址键：{@code ehr.client.base-url}；令牌键：{@code ehr.client.internal-token}（缺省复用 {@code SSO_INTERNAL_TOKEN}）。
 */
@AutoConfiguration
@EnableFeignClients(clients = {EhrOrgApi.class, EhrEmployeeApi.class})
public class EhrClientAutoConfiguration {
}
