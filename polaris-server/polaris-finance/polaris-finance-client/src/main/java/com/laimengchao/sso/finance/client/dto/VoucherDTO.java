package com.laimengchao.sso.finance.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 记账凭证。sourceNo 为业务单号（如合同验收单号），非雪花 ID。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VoucherDTO {

    private String no;
    private String sourceType;
    private String sourceNo;
    private String summary;
    private String subject;
    private BigDecimal amount;
    private String status;
    private String date;
}
