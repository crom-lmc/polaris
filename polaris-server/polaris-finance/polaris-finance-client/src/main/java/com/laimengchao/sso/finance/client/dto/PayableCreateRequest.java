package com.laimengchao.sso.finance.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 应付款登记请求。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayableCreateRequest {

    private String contractNo;
    private String party;
    private BigDecimal amount;
    private String dueDate;
    private String summary;
    private String orgCode;
}
