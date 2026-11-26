package com.laimengchao.sso.finance.module.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 预算额度：base 预算 + 历史归档；used/current 由凭证派生，合同承诺由合同域写入。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_budget")
public class FinBudget extends BaseEntity {

    private String dept;
    private Integer year;
    /** 年度预算额度 */
    private BigDecimal amount;
    /** 历史归档已用（不逐条展开） */
    private BigDecimal history;
    /** 合同承诺占用，由合同域跨服写入 */
    private BigDecimal contractCommitted;
    /** 合同已付，由合同域跨服写入 */
    private BigDecimal contractPaid;
    /** 合同在途，由合同域跨服写入 */
    private BigDecimal contractInFlight;
    private String orgCode;
}
