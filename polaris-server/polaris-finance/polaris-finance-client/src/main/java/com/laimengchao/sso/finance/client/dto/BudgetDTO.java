package com.laimengchao.sso.finance.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 部门年度预算摘要。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BudgetDTO {

    private String dept;
    private Integer year;
    private BigDecimal amount;
    private BigDecimal used;
    private BigDecimal available;
    /** 已占用（在途）金额 */
    private BigDecimal committed;
}
