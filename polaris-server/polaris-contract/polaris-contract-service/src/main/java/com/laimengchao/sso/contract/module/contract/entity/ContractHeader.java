package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 合同台账（采购 / 销售共用）：金额由清单合计派生，不存冗余金额。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_header")
public class ContractHeader extends BaseEntity {

    /** HT-CG / HT-XS-yyyy-XXXXXX */
    private String no;
    /** PURCHASE / SALES */
    private String type;
    private String name;
    private String partnerCode;
    /** 单向挂项目 */
    private String projectNo;
    private String dept;
    private String owner;
    private LocalDate signedDate;
    private LocalDate effectiveDate;
    private LocalDate endDate;
    /** DRAFT / PENDING / EFFECTIVE / EXECUTING / COMPLETED / TERMINATED */
    private String status;
    private String paymentTerms;
    /** 合同标的 */
    private String subject;
    /** 历史列：预算科目属财务域字段，合同侧不再录入或展示；保留列兼容存量数据 */
    private String budgetSubject;
    /** 来源：DIRECT 直接新建 / FROM_PLAN 采购计划转入 */
    private String contractSource;
    /** 来源采购计划号（FROM_PLAN 时由采购计划号带入，DIRECT 为空） */
    private String planNo;
    private String orgCode;
    /** 附件 file_id，逗号分隔（统一文件服务 contract_attachment 策略） */
    private String fileIds;

    /** 以下均为派生展示字段，不落库 */
    @TableField(exist = false)
    private BigDecimal amount;
    @TableField(exist = false)
    private BigDecimal paidAmount;
    @TableField(exist = false)
    private BigDecimal inFlightAmount;
    @TableField(exist = false)
    private BigDecimal pendingAmount;
    @TableField(exist = false)
    private BigDecimal progress;
    @TableField(exist = false)
    private String partnerName;
    @TableField(exist = false)
    private String projectName;
    @TableField(exist = false)
    private Integer itemCount;
    @TableField(exist = false)
    private Integer planCount;
}
