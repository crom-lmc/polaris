package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 业务项目档案：组织项目组只是协作边界，项目自身独立归属 org_code。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_project")
public class ContractProject extends BaseEntity {

    /** PRJ-yyyyMMdd-XXXXXX */
    private String code;
    private String name;
    /** SALES / PURCHASE / INTERNAL */
    private String type;
    /** 归属组织，由项目组带出 */
    private String orgCode;
    /** 组织项目组编码 */
    private String teamCode;
    /** 成本中心 = 公司主体编码（复用 EHR） */
    private String costCenterCode;
    private String owner;
    private BigDecimal budgetAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    /** DRAFT / ACTIVE / PAUSED / CLOSED / CANCELLED */
    private String status;
    private String remark;
}
