package com.laimengchao.sso.finance.module.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 财务域请求体集合（按单据类型分组，避免为每张表单建一个空壳类）。 */
public final class FinanceRequests {

    private FinanceRequests() {
    }

    @Data
    public static class SubjectSave {
        private String code;
        private String name;
        private Integer level;
        private String balanceDirection;
        private String category;
        private String status;
        private String orgCode;
    }

    @Data
    public static class BudgetSubjectSave {
        private String code;
        private String name;
        private Integer year;
        private BigDecimal annualAmount;
        private String accountSubjectCode;
        private String status;
        private String orgCode;
    }

    @Data
    public static class ExpenseSave {
        private String applicant;
        /** settings 字典 finance.expense_type 的 value；后端据此带出会计科目 */
        private String expenseType;
        private String reason;
        private BigDecimal amount;
        private String dept;
        /** 兼容历史数据；新单由 expenseType 映射生成，忽略客户端传值 */
        private String subject;
        /** 报销事由（settings 字典 finance.expense_reason 的 value） */
        private String expenseReason;
        /** 发票类型（settings 字典 finance.invoice_type 的 value） */
        private String invoiceType;
        /** 税率（settings 字典 finance.tax_rate） */
        private BigDecimal taxRate;
        /** 税额 */
        private BigDecimal taxAmount;
        private LocalDate date;
        private String orgCode;
        /** 报销凭证附件 file_id 列表，落库为逗号分隔字符串 */
        private List<String> fileIds;
    }

    @Data
    public static class VoucherSave {
        /** 凭证类型（settings 字典 finance.voucher_type） */
        private String voucherType;
        private String sourceType;
        private String sourceNo;
        private String summary;
        private String subject;
        private BigDecimal amount;
        private LocalDate date;
        private String creator;
        private String dept;
        private String contractNo;
        /** 凭证附件 file_id 列表 */
        private List<String> fileIds;
        private String orgCode;
    }

    @Data
    public static class ArApSave {
        private String party;
        /** AR 应收 / AP 应付 */
        private String type;
        private BigDecimal amount;
        private String contractNo;
        private String subject;
        private String summary;
        private LocalDate date;
        private String dept;
        /** 发票号 */
        private String invoiceNo;
        /** 发票类型（settings 字典 finance.invoice_type） */
        private String invoiceType;
        /** 账期天数 */
        private Integer creditDays;
        /** 到期日（不传则按 date + creditDays 派生） */
        private LocalDate dueDate;
        private String orgCode;
    }

    @Data
    public static class BudgetSave {
        private String dept;
        private Integer year;
        private BigDecimal amount;
        private BigDecimal history;
        private BigDecimal contractCommitted;
        private BigDecimal contractPaid;
        private BigDecimal contractInFlight;
        private String orgCode;
    }

    @Data
    public static class PaymentSave {
        private String contractNo;
        private String party;
        /** 付款阶段（settings 字典 contract.payment_stage） */
        private String stage;
        private BigDecimal amount;
        /** 付款事由 */
        private String summary;
        /** 计划付款日期 */
        private LocalDate dueDate;
        /** 合同域来源单据号 */
        private String sourceDocNo;
        /** 结算方式（settings 字典 finance.settle_method） */
        private String settleMethod;
        private LocalDate date;
        /** 合同与验收凭证 file_id 列表 */
        private List<String> fileIds;
        private String orgCode;
    }

    /** 核销请求：MANUAL 模式需指定目标单号 */
    @Data
    public static class WriteOffRequest {
        private BigDecimal amount;
        /** 管理端写核销用 targetNo；client 契约（polaris-finance-client）传 no */
        private String targetNo;
        private String no;
        private LocalDate date;
    }

    /** 销售合同验收推应收（由合同域跨服调用） */
    @Data
    public static class AcceptanceArRequest {
        private String acceptanceNo;
        private String contractNo;
        private String party;
        private BigDecimal amount;
        private LocalDate date;
        private String summary;
        private String dept;
        private String orgCode;
    }

    /** 合同域预算占用 / 释放 */
    @Data
    public static class BudgetOperateRequest {
        private String dept;
        private Integer year;
        /** 占用/释放金额，或合同已付金额 */
        private BigDecimal amount;
        /** 合同在途金额（仅 contract-stats 使用） */
        private BigDecimal inFlightAmount;
        private String contractNo;
    }

    /** 过账回写：由业务单生成凭证 */
    @Data
    public static class VoucherPostRequest {
        private String sourceType;
        private String sourceNo;
        private String summary;
        private String subject;
        private BigDecimal amount;
        private LocalDate date;
        private String creator;
        private String dept;
        private String contractNo;
        private String orgCode;
    }
}
