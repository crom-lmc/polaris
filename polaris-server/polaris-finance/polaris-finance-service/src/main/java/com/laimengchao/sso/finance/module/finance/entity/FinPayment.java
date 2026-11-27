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
    /** 付款阶段（settings 字典 contract.payment_stage） */
    private String stage;
    private BigDecimal amount;
    /** 付款事由 */
    private String summary;
    /** 计划付款日期 */
    private LocalDate dueDate;
    /** 合同域来源单据号（合同付款计划号） */
    private String sourceDocNo;
    /** 过账生成的凭证号 */
    private String voucherNo;
    /** 结算方式（settings 字典 finance.settle_method） */
    private String settleMethod;
    /** PENDING / PAID / CANCELLED */
    private String status;
    private LocalDate date;
    /** 合同与验收凭证 file_id，逗号分隔 */
    private String fileIds;
    private String orgCode;
}
