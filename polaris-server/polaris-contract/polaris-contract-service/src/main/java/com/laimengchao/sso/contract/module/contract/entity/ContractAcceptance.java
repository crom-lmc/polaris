package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 到货验收单：合同清单到货验收时生成，财务域可按单号反查过账状态。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_acceptance")
public class ContractAcceptance extends BaseEntity {

    /** JD-yyyyMMdd-XXXXXX */
    private String no;
    private String contractNo;
    /** 合同清单行 ID，跨服务以字符串承载 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;
    private String materialCode;
    private BigDecimal qty;
    private LocalDate date;
    /** 合格 / 部分合格 / 不合格 */
    private String result;
    /** DRAFT / POSTED */
    private String status;
    /** 过账生成的凭证号（财务域回写） */
    private String voucherNo;
    private String orgCode;
    /** 验收凭证 file_id，逗号分隔（统一文件服务 contract_attachment 策略） */
    private String fileIds;
}
