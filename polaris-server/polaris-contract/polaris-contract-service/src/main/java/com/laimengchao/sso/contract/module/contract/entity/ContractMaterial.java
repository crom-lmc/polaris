package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 物料：独立表，category_id 指向三级分类；物料为叶子，不参与分类树。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_material")
public class ContractMaterial extends BaseEntity {

    /** 所属三级分类 ID（contract_material_category.id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;
    /** 物料编码 = 三级分类码 + 6 位流水 */
    private String code;
    private String name;
    private String spec;
    private String unit;
    private BigDecimal referencePrice;
    private String orgCode;
}
