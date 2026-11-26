package com.laimengchao.sso.contract.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料 / 服务目录节点。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaterialDTO {

    private String code;
    private String name;
    private String spec;
    private String unit;
    private BigDecimal referencePrice;
}
