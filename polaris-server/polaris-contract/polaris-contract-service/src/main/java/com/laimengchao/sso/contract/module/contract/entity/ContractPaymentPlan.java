package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 合同付款计划：sourceDocNo 由财务域建单后回写，合同域不自造 YS/FK 单号。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_payment_plan")
public class ContractPaymentPlan extends BaseEntity {

    private String contractNo;
    private String stage;
    private LocalDate dueDate;
    private BigDecimal amount;
    /** 财务域来源单据号，可为空 */
    private String sourceDocNo;
    /** WAITING / PENDING / APPROVED / PAID / REJECTED */
    private String status;
    private String orgCode;
}
