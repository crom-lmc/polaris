package com.laimengchao.sso.finance.module.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 应收应付单：YS/YF-yyyyMMdd-XXXXXX，账龄按 date 派生，核销累计写 written_off_amount。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_ar_ap")
public class FinArAp extends BaseEntity {

    private String no;
    /** 往来单位名称/编码 */
    private String party;
    /** 应收 AR / 应付 AP */
    private String type;
    private BigDecimal amount;
    private String contractNo;
    private String voucherNo;
    /** DRAFT/PENDING/APPROVED/REJECTED/POSTED/PAID */
    private String status;
    private LocalDate date;
    private String subject;
    private String summary;
    private String paymentStage;
    private String creator;
    private String dept;
    /** 累计已核销金额，余额 = amount - written_off_amount */
    private BigDecimal writtenOffAmount;
    private String orgCode;
}
