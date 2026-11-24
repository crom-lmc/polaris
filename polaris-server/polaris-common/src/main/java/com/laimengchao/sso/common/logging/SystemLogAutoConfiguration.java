package com.laimengchao.sso.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 系统日志采集自动装配：凡依赖 polaris-common 的服务自动挂 Appender，无需各服务写配置类。
 * <p>
 * 开关 {@code polaris.system-log.enabled}（默认 true）。采集粒度默认 INFO（INFO/WARN/ERROR
 * 全量入库），各服务可在自身配置里覆盖 {@code polaris.system-log.min-level} 调整。
 * 审计服务自身必须关闭（audit.yaml 里设 false），否则「记录日志的日志」会自我循环。
 */
@AutoConfiguration
@ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
@ConditionalOnProperty(prefix = "polaris.system-log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SystemLogAutoConfiguration.Properties.class)
public class SystemLogAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SystemLogReporter systemLogReporter(Properties p, org.springframework.core.env.Environment env) {
        String app = p.getApp();
        if (app == null || app.isBlank() || "unknown".equals(app)) {
            app = env.getProperty("spring.application.name", "unknown");
        }
        // 默认复用平台内部令牌与审计地址环境变量，各服务无需单独配置即可上报
        String url = p.getUrl();
        if (url == null || url.isBlank() || "http://127.0.0.1:9012".equals(url)) {
            url = env.getProperty("AUDIT_SERVICE_URL", url);
        }
        String token = p.getInternalToken();
        if (token == null || token.isBlank()) {
            token = env.getProperty("SSO_INTERNAL_TOKEN", "");
        }
        return new SystemLogReporter(app, url == null ? "http://127.0.0.1:9012" : url,
                token, p.getQueueSize(), p.getBatchSize(), p.getFlushMs());
    }

    @Bean
    public InitializingBean systemLogAppenderInstaller(SystemLogReporter reporter, Properties p) {
        return () -> {
            if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
                return;
            }
            if (context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                    .getAppender("SYSTEM_LOG_APPENDER") != null) {
                return;
            }
            Level level = Level.toLevel(p.getMinLevel(), Level.WARN);
            SystemLogAppender appender = new SystemLogAppender(reporter, level);
            appender.setContext(context);
            appender.start();
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
        };
    }

    @ConfigurationProperties(prefix = "polaris.system-log")
    public static class Properties {
        /** 应用名，默认取 spring.application.name；由 Environment 解析后注入 */
        private String app = "unknown";
        /** audit-server 根地址 */
        private String url = "http://127.0.0.1:9012";
        private String internalToken = "";
        /** 采集最小级别，默认 INFO（全量采集 INFO/WARN/ERROR），按应用可覆盖 */
        private String minLevel = "INFO";
        private int queueSize = 2000;
        private int batchSize = 100;
        private long flushMs = 1000;

        public String getApp() { return app; }
        public void setApp(String app) { this.app = app; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getInternalToken() { return internalToken; }
        public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
        public String getMinLevel() { return minLevel; }
        public void setMinLevel(String minLevel) { this.minLevel = minLevel; }
        public int getQueueSize() { return queueSize; }
        public void setQueueSize(int queueSize) { this.queueSize = queueSize; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public long getFlushMs() { return flushMs; }
        public void setFlushMs(long flushMs) { this.flushMs = flushMs; }
    }
}
