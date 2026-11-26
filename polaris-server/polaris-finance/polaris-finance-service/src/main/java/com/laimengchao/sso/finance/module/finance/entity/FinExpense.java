package com.laimengchao.sso.finance.module.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 费用报销单：BX-yyyyMMdd-XXXXXX，由 settings 单号规则生成。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_expense")
public class FinExpense extends BaseEntity {

    private String no;
    private String applicant;
    private String reason;
    private BigDecimal amount;
    private String dept;
    private String subject;
    /** DRAFT/PENDING/APPROVED/REJECTED/POSTED/PAID */
    private String status;
    private String voucherNo;
    private LocalDate date;
    private String orgCode;
}
