package com.laimengchao.sso.finance.module.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 预算科目主数据：编码、名称、年度、额度、关联会计科目、启用状态。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_budget_subject")
public class FinBudgetSubject extends BaseEntity {

    private String code;
    private String name;
    private Integer year;
    private BigDecimal annualAmount;
    /** 关联会计科目编码 fin_subject.code */
    private String accountSubjectCode;
    /** ENABLED / DISABLED */
    private String status;
    private String orgCode;
}
