package com.laimengchao.sso.contract.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 合同明细行。id 为雪花 ID，按字符串承载，避免跨服务丢精度。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContractItemDTO {

    private String id;
    private String code;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal deliveredQty;
}
