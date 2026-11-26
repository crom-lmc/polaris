package com.laimengchao.sso.finance.module.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 会计凭证：PZ-yyyyMMdd-XXXXXX（按月重置），可由报销/应收应付过账或手工新增。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_voucher")
public class FinVoucher extends BaseEntity {

    private String no;
    /** EXPENSE / AR / AP / MANUAL 等来源类型 */
    private String sourceType;
    /** 来源业务单号，用于列表追溯 */
    private String sourceNo;
    private String summary;
    private String subject;
    private BigDecimal amount;
    /** DRAFT / POSTED */
    private String status;
    private LocalDate date;
    private String creator;
    private String dept;
    private String contractNo;
    private String orgCode;
}
