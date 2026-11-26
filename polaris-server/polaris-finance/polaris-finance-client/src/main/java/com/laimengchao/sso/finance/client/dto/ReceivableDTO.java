package com.laimengchao.sso.finance.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 应收款。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReceivableDTO {

    private String no;
    private String party;
    private String type;
    private BigDecimal amount;
    private String status;
    private String contractNo;
    private String voucherNo;
}
