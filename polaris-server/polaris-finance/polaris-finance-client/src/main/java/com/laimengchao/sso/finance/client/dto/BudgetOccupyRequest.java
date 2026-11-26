package com.laimengchao.sso.finance.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 预算占用请求。bizNo 为业务单号，用于幂等与后续释放。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BudgetOccupyRequest {

    private String dept;
    private Integer year;
    private BigDecimal amount;
    private String bizNo;
}
