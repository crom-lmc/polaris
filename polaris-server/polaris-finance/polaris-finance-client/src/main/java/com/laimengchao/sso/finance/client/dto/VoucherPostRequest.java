package com.laimengchao.sso.finance.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 凭证过账请求。sourceType + sourceNo 唯一确定一张业务来源凭证。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VoucherPostRequest {

    private String sourceType;
    private String sourceNo;
    private String summary;
    private String subject;
    private BigDecimal amount;
    private String dept;
    private String orgCode;
}
