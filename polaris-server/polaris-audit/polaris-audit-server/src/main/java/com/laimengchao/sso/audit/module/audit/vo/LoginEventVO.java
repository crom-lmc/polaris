package com.laimengchao.sso.audit.module.audit.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录审计视图。字段与原 sso_login_log 保持一致，审计中心前端无需感知底层单表模型。
 */
@Data
public class LoginEventVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 账号 ID；登录失败时为空 */
    private String accountId;

    /** 尝试登录的用户名 */
    private String username;

    private String loginType;

    /** success / fail */
    private String result;

    private String failReason;

    private String clientIp;

    private String userAgent;

    private LocalDateTime createdAt;
}
