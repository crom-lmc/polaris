package com.laimengchao.sso.contract.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 合同付款计划：sourceDocNo 由财务域建单后回写，合同域不自造 YS/FK 单号。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contract_payment_plan")
public class ContractPaymentPlan extends BaseEntity {

    /** 付款申请单号 FK-yyyy-XXXXXX（settings 单号规则，创建时生成） */
    private String no;
    private String contractNo;
    private String stage;
    /** contract_payment_term.id；空表示存量自由录入 */
    private Long termId;
    /** GOODS 阶段所选清单行 ID，逗号分隔 */
    private String itemIds;
    private LocalDate dueDate;
    private BigDecimal amount;
    /** 付款事由 */
    private String summary;
    /** 财务域来源单据号，可为空 */
    private String sourceDocNo;
    /** 财务域过账生成的凭证号，回写 */
    private String voucherNo;
    /** WAITING / PENDING / APPROVED / POSTED / PAID / REJECTED */
    private String status;
    private String orgCode;
    /** 合同与验收凭证 file_id，逗号分隔（统一文件服务 contract_attachment 策略） */
    private String fileIds;
}
