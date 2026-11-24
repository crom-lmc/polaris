package com.laimengchao.sso.audit.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统运行日志（各服务 Logback 异步上报），与 audit_event 分开存储。 */
@Data
@TableName("system_log")
public class SystemLog {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 来源应用 */
    private String app;

    /** TRACE/DEBUG/INFO/WARN/ERROR */
    private String level;

    private String logger;

    private String thread;

    /** 已脱敏的日志正文 */
    private String message;

    /** 已脱敏的异常堆栈 */
    private String stackTrace;

    private LocalDateTime createdAt;
}
