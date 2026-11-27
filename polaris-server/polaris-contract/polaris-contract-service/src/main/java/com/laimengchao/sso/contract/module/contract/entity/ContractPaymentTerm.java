package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 合同付款节点：建合同时配置，比例合计 100%；金额按合同清单合计 × 比例锁定。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_payment_term")
public class ContractPaymentTerm extends BaseEntity {

    private String contractNo;
    /** PREPAY / GOODS / ACCEPT / WARRANTY */
    private String stage;
    /** 比例%，如 20.00 */
    private BigDecimal ratio;
    /** 合同额 × 比例，建节点时锁定 */
    private BigDecimal amount;
    /** 计划付款日（选填） */
    private LocalDate dueDate;
    private String remark;
    private String orgCode;
}
