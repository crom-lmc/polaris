package com.laimengchao.sso.audit.client;

import com.laimengchao.sso.audit.client.api.AuditApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 引入 polaris-audit-client 即自动装配统一审计中心跨服客户端：
 * <ul>
 *   <li>{@link AuditApi}：{@code /internal/audit/**} 的事件上报与登录统计，凭 X-Internal-Token 准入。</li>
 * </ul>
 * 地址键：{@code audit.client.base-url}（缺省 {@code AUDIT_SERVICE_URL} / http://127.0.0.1:9012）；
 * 令牌键：{@code audit.client.internal-token}（缺省复用 {@code SSO_INTERNAL_TOKEN}）。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "audit.client", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableFeignClients(clients = {AuditApi.class})
public class AuditClientAutoConfiguration {
}
