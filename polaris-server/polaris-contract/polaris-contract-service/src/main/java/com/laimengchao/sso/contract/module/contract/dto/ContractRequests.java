package com.laimengchao.sso.contract.module.contract.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 合同域管理端请求体集合（按业务对象分组，避免每个动作一个文件）。 */
public final class ContractRequests {

    private ContractRequests() {
    }

    @Data
    public static class PartnerSave {
        private String code;
        private String name;
        private String subjectType;
        private String role;
        private String type;
        private String creditCode;
        private String legalPerson;
        private String registeredCapital;
        private LocalDate establishDate;
        private String taxpayerType;
        private String address;
        private String remark;
        private String contact;
        private String phone;
        private String terms;
        private String rating;
        private BigDecimal creditLimit;
        private BigDecimal creditUsed;
        private String status;
        private String bank;
        private String accountName;
        private String bankAccount;
        private String orgCode;
    }

    /** 分类新增/编辑：parentId 为父分类，编码服务端按层级续位 */
    @Data
    public static class MaterialCategorySave {
        private Long parentId;
        private String code;
        private String name;
        private String orgCode;
    }

    /** 物料新增/编辑：categoryId 为所属三级分类，编码服务端续位 */
    @Data
    public static class MaterialSave {
        private Long categoryId;
        /** 兼容旧调用：分类父级 ID，服务端以 categoryId 为准 */
        private Long parentId;
        private String code;
        private String name;
        /** 兼容旧调用，服务端固定 MATERIAL */
        private String kind;
        private String spec;
        private String unit;
        private BigDecimal referencePrice;
        private String orgCode;
    }

    @Data
    public static class ProjectSave {
        private String name;
        private String type;
        /** 归属组织由所选项目组带出，前端随项目组一并提交 */
        private String orgCode;
        private String teamCode;
        private String costCenterCode;
        private String owner;
        private BigDecimal budgetAmount;
        private LocalDate startDate;
        private LocalDate endDate;
        private String remark;
    }

    @Data
    public static class ContractSave {
        private String type;
        private String name;
        private String partnerCode;
        private String projectNo;
        private String dept;
        private String owner;
        private LocalDate signedDate;
        private LocalDate effectiveDate;
        private LocalDate endDate;
        /** 质保到期日：质保金节点放行依据 */
        private LocalDate warrantyDueDate;
        private String status;
        private String paymentTerms;
        /** 合同标的 */
        private String subject;
        /** 备注（非必填，200 字） */
        private String note;
        private String orgCode;
        /** 变更原因（仅用于变更留痕，不写入合同主表） */
        private String remark;
        /** 附件 file_id 列表；null 表示本次未提交附件字段，保留原值 */
        private List<String> fileIds;
    }

    @Data
    public static class ItemSave {
        private Long id;
        private Integer lineNo;
        private String materialCode;
        private String materialName;
        private String spec;
        private String unit;
        private BigDecimal qty;
        private BigDecimal price;
        private BigDecimal amount;
        private String remark;
    }

    @Data
    public static class AcceptRequest {
        /** 验收数量，必须大于 0 且不超过未交付数量 */
        private BigDecimal qty;
        private String result;
        private LocalDate date;
        private String remark;
        /** 客户验收单号（销售合同，选填） */
        private String customerNo;
        /** 验收凭证 file_id 列表 */
        private List<String> fileIds;
    }

    @Data
    public static class SignRequest {
        /** 签收数量，必须大于 0 且不超过未签收数量 */
        private BigDecimal qty;
        /** 签收日期，必填 */
        private LocalDate date;
        private String remark;
        /** 签收凭证 file_id 列表 */
        private List<String> fileIds;
    }

    @Data
    public static class PaymentPlanSave {
        private String stage;
        /** 引用的合同付款节点 ID；为空表示存量自由录入，沿用全验收门槛 */
        private Long termId;
        /** GOODS 阶段所选清单行 ID，逗号分隔；其他阶段为空 */
        private String itemIds;
        private LocalDate dueDate;
        private BigDecimal amount;
        /** 付款事由 */
        private String summary;
        private String status;
        /** 合同与验收凭证 file_id 列表；null 表示本次未提交，保留原值 */
        private List<String> fileIds;
    }

    /** 合同付款节点批量保存（一次性替换该合同全部节点） */
    @Data
    public static class PaymentTermSaveBatch {
        private List<PaymentTermSave> terms;
    }

    @Data
    public static class PaymentTermSave {
        /** 已有节点 ID，空表示新增 */
        private Long id;
        /** PREPAY / GOODS / ACCEPT / WARRANTY */
        private String stage;
        /** 比例% */
        private BigDecimal ratio;
        /** 计划付款日（选填） */
        private LocalDate dueDate;
        private String remark;
    }

    /** 付款申请：sourceDocNo 由财务域建单后回写，这里只接收不生成 */
    @Data
    public static class PaymentApplyRequest {
        private String sourceDocNo;
        /** 付款事由（发起申请时补充） */
        private String summary;
        /** 合同与验收凭证 file_id 列表 */
        private List<String> fileIds;
    }

    @Data
    public static class PurchasePlanSave {
        private String title;
        private String dept;
        private String applicant;
        /** 计划日期（设计稿 date） */
        private LocalDate date;
        private LocalDate needDate;
        private String orgCode;
        private List<PurchasePlanItemSave> items;
        /** 附件 file_id 列表；null 表示本次未提交附件字段，保留原值 */
        private List<String> fileIds;
    }

    @Data
    public static class PurchasePlanItemSave {
        private String materialCode;
        private String spec;
        private String unit;
        private BigDecimal qty;
        private BigDecimal price;
    }

    /** 采购计划转采购合同：合同主信息由调用方补充，清单从计划明细带入 */
    @Data
    public static class PlanConvertRequest {
        /** 合同名称，缺省用采购计划名称 */
        private String name;
        private String partnerCode;
        private String projectNo;
        private String dept;
        private String owner;
        private LocalDate signedDate;
        private LocalDate effectiveDate;
        private LocalDate endDate;
        /** 备注（非必填，200 字） */
        private String note;
        /** 附件 file_id 列表，缺省沿用采购计划附件 */
        private List<String> fileIds;
        private String paymentTerms;
        /** 合同标的（历史列，前端不再录入） */
        private String subject;
        private String orgCode;
    }

}
