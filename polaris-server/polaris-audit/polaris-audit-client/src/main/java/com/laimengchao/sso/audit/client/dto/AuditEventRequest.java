package com.laimengchao.sso.audit.client.dto;

import lombok.Data;

/**
 * 统一审计事件上报请求（网关、账号中心、EHR 共用）。
 * <p>
 * 操作人规则（用户 2026-11-23 明确）：
 * <ul>
 *   <li>有登录主体的动作：operatorType=USER，operatorId/operatorName 填真实用户；</li>
 *   <li>无登录主体的内部 Feign 调用：operatorType=SYSTEM，operatorName=SYSTEM，不得因 id 为空丢弃；</li>
 *   <li>登录成功/失败：operatorType=USER，operatorName 填<b>尝试登录的用户名</b>（失败时用户可能不存在也照记，
 *       用于排查撞库），不用 SYSTEM。</li>
 * </ul>
 * loginType / result / failReason 只在 LOGIN 事件里有值，落库后供登录审计页与概览统计直接使用。
 */
@Data
public class AuditEventRequest {

    /** 来源应用：sso / ehr / cms / forum / file / openapi / job / settings / portal */
    private String app;

    /** 事件类别：ACCESS（网关通用访问）/ LOGIN（登录）/ BUSINESS（业务补语义）/ SENSITIVE（敏感操作） */
    private String eventCategory;

    /** 动作码：网关为 METHOD + path，登录为 LOGIN_SUCCESS / LOGIN_FAIL，敏感操作为 VIEW / PLAIN / EXPORT 等 */
    private String action;

    private String httpMethod;

    private String requestPath;

    /** USER / SYSTEM */
    private String operatorType;

    /** 操作人账号 ID，SYSTEM 或登录失败时可为 null */
    private String operatorId;

    private String operatorName;

    private String targetType;

    private String targetId;

    /** 非敏感详情，如 result=success 及对象变更摘要；禁止写密码/令牌 */
    private String detail;

    /** 请求入参（已脱敏）：query 参数 + JSON body 摘要，非 JSON/文件流不采集 */
    private String requestParams;

    /** HTTP 状态码，网关在响应完成后回填；空表示非网关事件 */
    private Integer httpStatus;

    /** 业务返回码（R.code），AOP 在业务方法返回后回填；空表示未知 */
    private Integer bizCode;

    /** 响应结果摘要（已脱敏）：R.code/msg 或 HTTP 状态描述 */
    private String responseSummary;

    /** 处理耗时（毫秒），网关从请求进入到响应完成 */
    private Long costMs;

    private String clientIp;

    private String userAgent;

    /** 登录方式：password / email / wechat */
    private String loginType;

    /** success / fail */
    private String result;

    /** 登录失败原因 */
    private String failReason;

    /** 事件发生时间（epoch 毫秒），为空由服务端取当前时间 */
    private Long occurredAt;
}
