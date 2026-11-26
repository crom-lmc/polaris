package com.laimengchao.sso.finance.module.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        private String reason;
        private BigDecimal amount;
        private String dept;
        private String subject;
        private LocalDate date;
        private String orgCode;
    }

    @Data
    public static class VoucherSave {
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
        private BigDecimal amount;
        private LocalDate date;
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
