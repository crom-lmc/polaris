package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 到货签收单：合同清单行到货签收时生成，作为到货款放行依据，与验收单相互独立。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_sign")
public class ContractSign extends BaseEntity {

    /** QS-yyyyMMdd-XXXXXX */
    private String no;
    private String contractNo;
    /** 合同清单行 ID，跨服务以字符串承载 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;
    private String materialCode;
    private BigDecimal qty;
    private LocalDate date;
    /** 签收人名称（AccountContext 解析，跨服务审批场景兜底为空） */
    private String signer;
    private String remark;
    /** DRAFT：签收留痕单，无后续状态流转 */
    private String status;
    private String orgCode;
    /** 签收凭证 file_id，逗号分隔 */
    private String fileIds;
}
