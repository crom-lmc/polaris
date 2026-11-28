package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 合同清单：金额由 qty × price 计算，到货验收累加 deliveredQty。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_item")
public class ContractItem extends BaseEntity {

    private String contractNo;
    private Integer lineNo;
    private String materialCode;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal amount;
    /** 已到货签收数量（到货款放行依据，与 deliveredQty 验收数量独立） */
    private BigDecimal signedQty;
    private BigDecimal deliveredQty;
    private String remark;
    private String orgCode;
}
