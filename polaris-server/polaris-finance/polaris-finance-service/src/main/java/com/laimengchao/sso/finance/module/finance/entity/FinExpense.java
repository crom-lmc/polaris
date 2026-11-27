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
    /** settings 字典 finance.expense_type 的 value（报销类型） */
    private String expenseType;
    private String reason;
    private BigDecimal amount;
    private String dept;
    /** 由报销类型映射带出的会计科目名称，过账时写入凭证 */
    private String subject;
    /** 报销类型映射的会计科目编码，便于对账与追溯 */
    private String subjectCode;
    /** 报销事由（settings 字典 finance.expense_reason 的 value） */
    private String expenseReason;
    /** 发票类型（settings 字典 finance.invoice_type 的 value） */
    private String invoiceType;
    /** 税率百分数（settings 字典 finance.tax_rate 的 value；如 6 表示 6%） */
    private BigDecimal taxRate;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 报销凭证附件 file_id，逗号分隔；文件本体由统一文件服务托管 */
    private String fileIds;
    /** DRAFT/PENDING/APPROVED/REJECTED/POSTED/PAID */
    private String status;
    private String voucherNo;
    private LocalDate date;
    private String orgCode;
}
