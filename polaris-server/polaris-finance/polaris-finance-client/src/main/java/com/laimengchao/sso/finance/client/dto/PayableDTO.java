package com.laimengchao.sso.finance.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 应付款。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayableDTO {

    private String no;
    private String contractNo;
    private String party;
    private BigDecimal amount;
    private String status;
    private String dueDate;
}
