package com.laimengchao.sso.finance.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 应收款登记请求。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReceivableCreateRequest {

    private String party;
    private BigDecimal amount;
    private String contractNo;
    private String summary;
    private String date;
    private String orgCode;
}
