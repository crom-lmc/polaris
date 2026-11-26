package com.laimengchao.sso.finance.module.finance.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 财务域展示对象集合：所有 Long 字段（含主键）统一字符串化，避免前端丢精度。 */
public final class FinanceVOs {

    private FinanceVOs() {
    }

    @Data
    public static class SubjectVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String code;
        private String name;
        private Integer level;
        private String balanceDirection;
        private String category;
        private String status;
        private String orgCode;
        private LocalDateTime createdAt;
    }

    @Data
    public static class BudgetSubjectVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String code;
        private String name;
        private Integer year;
        private BigDecimal annualAmount;
        private String accountSubjectCode;
        private String status;
        private String orgCode;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ExpenseVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String no;
        private String applicant;
        private String reason;
        private BigDecimal amount;
        private String dept;
        private String subject;
        private String status;
        private String voucherNo;
        private LocalDate date;
        private String orgCode;
        private LocalDateTime createdAt;
    }

    @Data
    public static class VoucherVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String no;
        private String sourceType;
        private String sourceNo;
        private String summary;
        private String subject;
        private BigDecimal amount;
        private String status;
        private LocalDate date;
        private String creator;
        private String dept;
        private String contractNo;
        private String orgCode;
        private LocalDateTime createdAt;
    }

    @Data
    public static class ArApVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String no;
        private String party;
        private String type;
        private BigDecimal amount;
        /** 未核销余额 = amount - writtenOffAmount */
        private BigDecimal balance;
        private Integer ageDays;
        private String contractNo;
        private String voucherNo;
        private String status;
        private LocalDate date;
        private String subject;
        private String summary;
        private String paymentStage;
        private String dept;
        private String orgCode;
        private LocalDateTime createdAt;
    }

    @Data
    public static class BudgetVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String dept;
        private Integer year;
        private BigDecimal amount;
        private BigDecimal history;
        /** 本期已用（凭证派生） */
        private BigDecimal current;
        /** 历史 + 本期 */
        private BigDecimal used;
        /** 可用额度 = amount - used - contractCommitted */
        private BigDecimal available;
        /** 执行率（%），按埋点预警线判定 alertFlag */
        private Integer rate;
        private String alertFlag;
        private BigDecimal contractCommitted;
        private BigDecimal contractPaid;
        private BigDecimal contractInFlight;
        private String orgCode;
        private LocalDateTime createdAt;
    }

    @Data
    public static class AgingBucketVO {
        private String label;
        private int minDays;
        private int maxDays;
        private BigDecimal value;
        private Integer percent;
    }

    @Data
    public static class PaymentVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String no;
        private String contractNo;
        private String party;
        private BigDecimal amount;
        private String status;
        private LocalDate date;
        private String orgCode;
        private LocalDateTime createdAt;
    }
}
