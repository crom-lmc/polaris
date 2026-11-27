package com.laimengchao.sso.finance.module.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 会计凭证：PZ-yyyyMMdd-XXXXXX（按月重置），可由报销/应收应付过账或手工新增。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_voucher")
public class FinVoucher extends BaseEntity {

    private String no;
    /** 凭证类型：settings 字典 finance.voucher_type 的 value（记账/收款/付款/转账） */
    private String voucherType;
    /** EXPENSE / AR / AP / MANUAL 等来源类型 */
    private String sourceType;
    /** 来源业务单号，用于列表追溯 */
    private String sourceNo;
    private String summary;
    private String subject;
    private BigDecimal amount;
    /** DRAFT / REVIEW / POSTED（REVIEW=待审核） */
    private String status;
    private LocalDate date;
    private String creator;
    /** 审核人（审核通过时写入） */
    private String reviewer;
    /** 过账人（过账时写入） */
    private String poster;
    private String dept;
    private String contractNo;
    /** 凭证附件 file_id，逗号分隔（统一文件服务 finance_expense_attachment 策略） */
    private String fileIds;
    private String orgCode;
}
