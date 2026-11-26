package com.laimengchao.sso.finance.client;

import com.laimengchao.sso.finance.client.api.BudgetApi;
import com.laimengchao.sso.finance.client.api.PayableApi;
import com.laimengchao.sso.finance.client.api.ReceivableApi;
import com.laimengchao.sso.finance.client.api.VoucherApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 引入 polaris-finance-client 即自动装配财务域服务间 Feign 客户端。
 * 地址键：{@code finance.client.base-url}；令牌键：{@code finance.client.internal-token}（缺省复用 {@code SSO_INTERNAL_TOKEN}）。
 */
@AutoConfiguration
@EnableFeignClients(clients = {
        BudgetApi.class,
        VoucherApi.class,
        PayableApi.class,
        ReceivableApi.class
})
public class FinanceClientAutoConfiguration {
}
