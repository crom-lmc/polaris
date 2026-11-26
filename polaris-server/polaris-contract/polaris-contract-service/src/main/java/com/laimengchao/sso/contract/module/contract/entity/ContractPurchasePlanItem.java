package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 采购计划明细：从物料库选物料。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_purchase_plan_item")
public class ContractPurchasePlanItem extends BaseEntity {

    private String planNo;
    private String materialCode;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal amount;
    private String orgCode;
}
