package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 往来单位：企业与个人共用一张表，个人时 creditCode 存身份证号。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_partner")
public class ContractPartner extends BaseEntity {

    private String code;
    private String name;
    /** COMPANY / PERSON */
    private String subjectType;
    /** SUPPLIER / CUSTOMER / BOTH */
    private String role;
    private String type;
    /** 统一社会信用代码；个人主体为身份证号 */
    private String creditCode;
    private String legalPerson;
    private String registeredCapital;
    private LocalDate establishDate;
    private String taxpayerType;
    private String address;
    private String remark;
    private String contact;
    private String phone;
    private String terms;
    /** A / B / C */
    private String rating;
    /** 授信额度；0=不限制（销售信用校验埋点开启时生效） */
    private BigDecimal creditLimit;
    /** 已占用额度（手工维护） */
    private BigDecimal creditUsed;
    /** COOPERATING / PENDING / STOPPED */
    private String status;
    private String bank;
    private String accountName;
    private String bankAccount;
    /** 归属组织（数据权限字段） */
    private String orgCode;

    /** 银行账号是否脱敏展示（不落库） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean bankMasked;
}
