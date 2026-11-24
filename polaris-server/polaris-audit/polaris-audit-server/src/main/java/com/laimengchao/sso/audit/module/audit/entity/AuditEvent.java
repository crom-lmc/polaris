package com.laimengchao.sso.audit.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台统一审计事件。网关 ACCESS、账号 LOGIN/SENSITIVE、EHR SENSITIVE 全部落本表。
 * 只追加、不修改、不删除；按保留期按天清理。
 */
@Data
@TableName("audit_event")
public class AuditEvent {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 来源应用编码 */
    private String app;

    /** ACCESS / LOGIN / BUSINESS / SENSITIVE */
    private String eventCategory;

    private String action;

    private String httpMethod;

    private String requestPath;

    /** USER / SYSTEM */
    private String operatorType;

    /** 账号 ID；SYSTEM 或登录失败可为 null */
    private String operatorId;

    /** 用户名；登录事件填尝试登录的用户名 */
    private String operatorName;

    private String targetType;

    private String targetId;

    /** result=success/fail 及非敏感详情 */
    private String detail;

    /** 请求入参（已脱敏，varchar 截断存储） */
    private String requestParams;

    /** HTTP 状态码 */
    private Integer httpStatus;

    /** 业务返回码 R.code */
    private Integer bizCode;

    /** 响应结果摘要（已脱敏） */
    private String responseSummary;

    /** 处理耗时（毫秒） */
    private Long costMs;

    private String clientIp;

    private String userAgent;

    /** 登录事件专有字段 */
    private String loginType;

    /** success / fail */
    private String result;

    /** 登录失败原因 */
    private String failReason;

    private LocalDateTime createdAt;
}
