package com.laimengchao.sso.audit.client;

import com.laimengchao.sso.audit.client.api.AuditApi;
import com.laimengchao.sso.audit.client.aspect.AuditLogAspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 业务方法审计切面自动装配。
 * <p>
 * 各业务服务只要依赖 polaris-audit-client，本切面即生效——不依赖 scanBasePackages，
 * 通过 AutoConfiguration.imports 注册。可用 {@code audit.log.enabled=false} 单独关闭留痕（上报仍保留）。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "audit.log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditLogAutoConfiguration {

    @Bean
    public AuditLogAspect auditLogAspect(AuditApi auditApi,
                                          @Value("${audit.log.enabled:true}") boolean enabled) {
        return new AuditLogAspect(auditApi, enabled);
    }
}
