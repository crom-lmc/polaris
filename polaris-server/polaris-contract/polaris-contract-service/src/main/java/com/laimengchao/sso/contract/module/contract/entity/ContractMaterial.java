package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/** 物料三级树：分类与物料共用一棵树，物料为三级分类下的叶子。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_material")
public class ContractMaterial extends BaseEntity {

    /** 父节点 ID，根节点为 null */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;
    /** 分类 01/0101/010101；物料 = 三级分类码 + 6 位流水 */
    private String code;
    private String name;
    /** CATEGORY / MATERIAL */
    private String kind;
    private String spec;
    private String unit;
    private BigDecimal referencePrice;
    private String orgCode;

    /** 树形展示用，不落库 */
    @TableField(exist = false)
    private List<ContractMaterial> children;
}
