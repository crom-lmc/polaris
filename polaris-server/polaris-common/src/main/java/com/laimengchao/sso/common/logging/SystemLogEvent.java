package com.laimengchao.sso.common.logging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统运行日志上报条目（跨服务传输契约）。
 * <p>
 * 与审计事件 {@code AuditEventRequest} 分开：审计记「谁做了什么」，本对象记「应用内部打了什么 log」，
 * 二者字段语义与生命周期都不同，因此分表存储、分页独立。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemLogEvent {

    /** 来源应用（spring.application.name） */
    private String app;

    /** 日志级别：TRACE/DEBUG/INFO/WARN/ERROR */
    private String level;

    /** 打日志的 logger 名（通常是类全限定名） */
    private String logger;

    /** 线程名 */
    private String thread;

    /** 日志正文（已脱敏、已截断） */
    private String message;

    /** 异常堆栈（已截断，无异常为空串） */
    private String stackTrace;

    /** 日志发生时间（epoch 毫秒） */
    private long timestamp;
}
