package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 物料三级分类：分类独立成表，parent_id 组成三级树，物料不进本表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_material_category")
public class ContractMaterialCategory extends BaseEntity {

    /** 父分类 ID，根节点为 null */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;
    /** 分类编码 01 / 0101 / 010101 */
    private String code;
    private String name;
    private String orgCode;

    /** 树形展示用，不落库 */
    @TableField(exist = false)
    private List<ContractMaterialCategory> children;
}
