package com.laimengchao.sso.contract.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 项目主数据。orgCode / teamCode / costCenterCode 对应 settings 侧编码。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectDTO {

    private String code;
    private String name;
    private String type;
    private String orgCode;
    private String teamCode;
    private String costCenterCode;
    private String owner;
    private BigDecimal budgetAmount;
    private String status;
    private String startDate;
    private String endDate;
}
