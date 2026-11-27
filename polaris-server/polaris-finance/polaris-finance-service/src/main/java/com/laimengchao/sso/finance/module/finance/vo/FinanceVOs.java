package com.laimengchao.sso.finance.module.finance.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        /** 报销类型字典 value */
        private String expenseType;
        /** 报销类型字典 label，供列表直接展示 */
        private String expenseTypeName;
        private String reason;
        private BigDecimal amount;
        private String dept;
        private String subject;
        private String subjectCode;
        /** 报销事由字典 value */
        private String expenseReason;
        /** 发票类型字典 value */
        private String invoiceType;
        /** 税率 */
        private BigDecimal taxRate;
        /** 税额 */
        private BigDecimal taxAmount;
        private String status;
        private String voucherNo;
        private LocalDate date;
        private String orgCode;
        /** 报销凭证附件 file_id 列表 */
        private List<String> fileIds;
        /** 后端签发的临时预览 URL，与 fileIds 顺序一致 */
        private List<String> attachmentUrls;
        private LocalDateTime createdAt;
    }

    /** 发起报销时按当前登录账号从 EHR 反查的申请人/归属部门，避免前端用雪花 ID 匹配员工丢精度 */
    @Data
    public static class ExpenseProfileVO {
        private String applicant;
        private String dept;
    }

    @Data
    public static class VoucherVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String no;
        /** 凭证类型字典 value */
        private String voucherType;
        private String sourceType;
        private String sourceNo;
        private String summary;
        private String subject;
        private BigDecimal amount;
        private String status;
        private LocalDate date;
        private String creator;
        /** 审核人 */
        private String reviewer;
        /** 过账人 */
        private String poster;
        private String dept;
        private String contractNo;
        private String orgCode;
        /** 凭证附件 file_id 列表 */
        private java.util.List<String> fileIds;
        /** 凭证附件只读预览 URL */
        private java.util.List<String> attachmentUrls;
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
        /** 发票号 */
        private String invoiceNo;
        /** 发票类型字典 value */
        private String invoiceType;
        /** 账期天数 */
        private Integer creditDays;
        /** 到期日 */
        private LocalDate dueDate;
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
        /** 付款阶段字典 value */
        private String stage;
        private BigDecimal amount;
        /** 付款事由 */
        private String summary;
        /** 计划付款日期 */
        private LocalDate dueDate;
        /** 合同域来源单据号 */
        private String sourceDocNo;
        /** 过账凭证号 */
        private String voucherNo;
        /** 结算方式字典 value */
        private String settleMethod;
        private String status;
        private LocalDate date;
        private String orgCode;
        /** 合同与验收凭证 file_id 列表 */
        private java.util.List<String> fileIds;
        /** 合同与验收凭证只读预览 URL */
        private java.util.List<String> attachmentUrls;
        private LocalDateTime createdAt;
    }
}
