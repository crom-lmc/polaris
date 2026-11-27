package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 采购计划：审批通过后转采购合同并带入清单。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_purchase_plan")
public class ContractPurchasePlan extends BaseEntity {

    /** CGJH-yyyy-XXXXXX */
    private String no;
    private String title;
    private String dept;
    private String applicant;
    /** 计划日期（设计稿 date；与需求日期 needDate 区分） */
    private LocalDate date;
    private LocalDate needDate;
    /** 历史列：预算科目属财务域字段，采购计划侧不再录入或展示；保留列兼容存量数据 */
    private String budgetSubject;
    /** DRAFT / PENDING / APPROVED / CONVERTED / REJECTED */
    private String status;
    private String contractNo;
    private String orgCode;
    /** 附件 file_id，逗号分隔（统一文件服务 contract_attachment 策略） */
    private String fileIds;

    /** 派生展示字段，不落库 */
    @TableField(exist = false)
    private BigDecimal amount;
    @TableField(exist = false)
    private Integer itemCount;
    @TableField(exist = false)
    private List<ContractPurchasePlanItem> items;
}
