package com.laimengchao.sso.openapi.module.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 接口调用日志实体（进出统一，按 direction 区分）。 */
@Data
@TableName("api_call_log")
public class ApiCallLog {

    @TableId(type = IdType.ASSIGN_ID)
    /** 雪花 ID 超过 JS 安全整数范围，序列化为字符串，避免前端点击详情时精度丢失查不到记录。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String traceId;
    private String direction;
    private String appCode;
    private String apiCode;
    private String channel;
    private String httpMethod;
    private String requestPath;
    private String reqMasked;
    private String respMasked;
    private Integer httpStatus;
    private String bizCode;
    private Integer costMs;
    private String clientIp;
    private String errorMsg;
    private LocalDateTime createdAt;

    /** 展示用：接口名称（由 apiCode 关联 api_open_endpoint，非库字段） */
    @TableField(exist = false)
    private String apiName;
    /** 展示用：调用方第三方应用名称（由 appCode 关联账号中心应用，非库字段） */
    @TableField(exist = false)
    private String appName;
    /** 展示用：出站渠道名称（由 channel 关联 api_outbound_channel，非库字段） */
    @TableField(exist = false)
    private String channelName;
}
