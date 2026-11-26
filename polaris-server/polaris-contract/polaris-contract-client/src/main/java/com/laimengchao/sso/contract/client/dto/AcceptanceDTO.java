package com.laimengchao.sso.contract.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 验收单。itemId 为雪花 ID，按字符串承载。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AcceptanceDTO {

    private String no;
    private String contractNo;
    private String itemId;
    private BigDecimal qty;
    private String date;
    private String result;
}
