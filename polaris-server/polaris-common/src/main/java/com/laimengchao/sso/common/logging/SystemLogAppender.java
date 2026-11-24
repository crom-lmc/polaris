package com.laimengchao.sso.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

/**
 * Logback Appender：把本服务打的日志转成 {@link SystemLogEvent} 交给上报器。
 * <p>
 * 只挂 root logger 一个实例，按配置的最小级别过滤（默认 WARN）；
 * appender 内不做任何日志输出，避免递归。
 */
public class SystemLogAppender extends AppenderBase<ILoggingEvent> {

    private final SystemLogReporter reporter;
    private final Level minLevel;

    public SystemLogAppender(SystemLogReporter reporter, Level minLevel) {
        this.reporter = reporter;
        this.minLevel = minLevel;
        setName("SYSTEM_LOG_APPENDER");
    }

    @Override
    protected void append(ILoggingEvent event) {
        // 防递归：上报线程自身打的日志（HTTP 客户端内部日志也走 root）不再入队
        if ("system-log-reporter".equals(Thread.currentThread().getName())) return;
        if (event.getLevel().toInt() < minLevel.toInt()) return;
        try {
            IThrowableProxy throwable = event.getThrowableProxy();
            String stack = throwable == null ? "" : SystemLogReporter.mask(
                    ThrowableProxyUtil.asString(throwable), SystemLogReporter.maxStack());
            SystemLogEvent item = new SystemLogEvent(
                    null,
                    event.getLevel().levelStr,
                    event.getLoggerName() == null ? "" : event.getLoggerName(),
                    event.getThreadName() == null ? "" : event.getThreadName(),
                    SystemLogReporter.mask(event.getFormattedMessage(), SystemLogReporter.maxMessage()),
                    stack,
                    event.getTimeStamp());
            reporter.offer(item);
        } catch (Throwable ignored) {
            // 采集本身绝不能抛回 Logback，更不能打日志造成递归
        }
    }
}
