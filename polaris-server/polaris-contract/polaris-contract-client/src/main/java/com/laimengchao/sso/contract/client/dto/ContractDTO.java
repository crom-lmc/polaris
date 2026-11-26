package com.laimengchao.sso.contract.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 合同台账摘要：金额类字段供财务域做在途 / 待付校验。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContractDTO {

    private String no;
    private String type;
    private String name;
    private String partnerCode;
    private String projectNo;
    private String dept;
    private String owner;
    private String status;
    private String endDate;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    /** 在途金额（审批/付款中） */
    private BigDecimal inFlightAmount;
    /** 待付金额 */
    private BigDecimal pendingAmount;
}
