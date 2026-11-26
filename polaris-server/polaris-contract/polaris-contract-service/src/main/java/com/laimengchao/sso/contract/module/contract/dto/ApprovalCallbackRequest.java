package com.laimengchao.sso.contract.module.contract.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批中心终审回调（settings ApprovalCallbackService.buildPayload）。
 * 字段契约由本服务自己守护，刻意不复用 settings 内部类。
 * instanceId / bizId 均为字符串（19 位雪花 ID）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalCallbackRequest {

    private String instanceId;
    private String bizType;
    private String bizId;
    private String bizNo;
    private String subjectId;
    private String bizIdKind;

    /** 0 进行中 / 1 已通过 / 2 已驳回 / 3 已撤回 / 4 已终止 */
    private Integer result;
    private String resultName;
    private Boolean approved;

    private Long applicantId;
    private String applicantName;
    private String templateCode;

    private Map<String, Object> formData = new LinkedHashMap<>();
    private String finishedAt;
}
