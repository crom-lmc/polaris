package com.laimengchao.sso.finance.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 应收核销请求。no 为应收款单号。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WriteOffRequest {

    private String no;
    private BigDecimal amount;
    private String date;
}
