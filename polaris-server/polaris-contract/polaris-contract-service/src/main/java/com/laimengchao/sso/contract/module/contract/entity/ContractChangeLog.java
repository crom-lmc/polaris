package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 合同变更记录：每次金额/条款/日期等变更留痕，支撑变更次数上限埋点与审计。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_change_log")
public class ContractChangeLog extends BaseEntity {

    /** BG-yyyyMMdd-XXXXXX（settings 单号规则） */
    private String no;
    /** contract_header.no */
    private String contractNo;
    /** AMOUNT / TERMS / DATE / PARTNER / OTHER */
    private String changeType;
    /** 变更前快照 */
    private String beforeValue;
    /** 变更后快照 */
    private String afterValue;
    /** 变更原因 */
    private String reason;
    /** 操作人 */
    private String operator;
    /** 归属组织（随主合同，数据权限） */
    private String orgCode;
}
