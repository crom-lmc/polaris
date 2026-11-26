package com.laimengchao.sso.finance.module.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 付款单：FK-yyyyMMdd-XXXXXX，由合同付款申请跨服生成。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_payment")
public class FinPayment extends BaseEntity {

    private String no;
    private String contractNo;
    private String party;
    private BigDecimal amount;
    /** PENDING / PAID / CANCELLED */
    private String status;
    private LocalDate date;
    private String orgCode;
}
