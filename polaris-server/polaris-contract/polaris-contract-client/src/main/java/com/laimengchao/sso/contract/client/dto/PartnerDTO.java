package com.laimengchao.sso.contract.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 往来单位（客户 / 供应商）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PartnerDTO {

    private String code;
    private String name;
    /** 角色：CUSTOMER / SUPPLIER / BOTH 等 */
    private String role;
    /** 主体类型：企业 / 个人 等 */
    private String subjectType;
    private String contact;
    private String phone;
    /** 付款/结算条款 */
    private String terms;
    private BigDecimal rating;
    private String status;
    /** 开户行 */
    private String bank;
    /** 银行账号 */
    private String bankAccount;
    /** 开户名称 */
    private String accountName;
    /** 税号 */
    private String taxNo;
    private String address;
    private String remark;
}
