package com.laimengchao.sso.contract.module.contract.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 合同域响应 VO：所有雪花 ID（含外键）统一序列化为字符串。 */
public final class ContractVOs {

    private ContractVOs() {
    }

    @Data
    public static class PartnerVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String code;
        private String name;
        private String subjectType;
        private String role;
        private String type;
        private String creditCode;
        /** 与 contract-client 的 PartnerDTO.taxNo 对齐 */
        private String taxNo;
        private String legalPerson;
        private String registeredCapital;
        private String establishDate;
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
        private Boolean bankMasked;
        private String orgCode;
    }

    /** 物料三级分类树节点：分类独立表，树里只有分类，不挂物料 */
    @Data
    public static class MaterialCategoryVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long parentId;
        private String code;
        private String name;
        /** ENABLED 启用 / DISABLED 停用 */
        private String status;
        private String orgCode;
        /** 直接子分类数 / 直接挂载物料数，供前端禁用删除按钮 */
        private Long childCount;
        private Long materialCount;
        private Boolean canDelete;
        private List<MaterialCategoryVO> children;
    }

    /** 物料：categoryId 指向三级分类；kind/children 仅供内部拼合树兼容使用 */
    @Data
    public static class MaterialVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        /** 所属三级分类 ID */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long categoryId;
        /** 兼容字段：分类节点为父分类 ID，物料节点为所属三级分类 ID */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long parentId;
        private String code;
        private String name;
        /** 所属分类名称：选料搜索结果展示用 */
        private String categoryName;
        /** 兼容字段：CATEGORY / MATERIAL */
        private String kind;
        private String spec;
        private String unit;
        private BigDecimal referencePrice;
        private String orgCode;
        private List<MaterialVO> children;
    }

    @Data
    public static class ProjectVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String code;
        private String name;
        private String type;
        private String orgCode;
        private String teamCode;
        private String costCenterCode;
        private String owner;
        private BigDecimal budgetAmount;
        private String startDate;
        private String endDate;
        private String status;
        private String remark;
        private Integer contractCount;
        private BigDecimal contractAmount;
        private BigDecimal usedAmount;
    }

    @Data
    public static class ContractVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String no;
        private String type;
        private String name;
        private String partnerCode;
        private String partnerName;
        private String projectNo;
        private String projectName;
        private String dept;
        private String owner;
        private String signedDate;
        private String effectiveDate;
        private String endDate;
        private String status;
        private String paymentTerms;
        /** 合同标的 */
        private String subject;
        /** 备注（非必填，200 字） */
        private String note;
        /** 来源 DIRECT / FROM_PLAN */
        private String contractSource;
        /** 来源采购计划号（FROM_PLAN 时带入） */
        private String planNo;
        private String orgCode;
        private BigDecimal amount;
        private BigDecimal paidAmount;
        private BigDecimal inFlightAmount;
        private BigDecimal pendingAmount;
        private BigDecimal progress;
        private Integer itemCount;
        /** 清单是否全部验收完成（派生：全部行 deliveredQty >= qty），前端据此隐藏「验收」入口 */
        private Boolean acceptanceDone;
        private Integer planCount;
        private Integer changeCount;
        /** 附件 file_id 列表，顺序与 attachmentUrls 一致 */
        private List<String> fileIds;
        /** 附件只读预览 URL（统一文件服务临时签名） */
        private List<String> attachmentUrls;
    }

    @Data
    public static class ChangeLogVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String no;
        private String changeType;
        private String beforeValue;
        private String afterValue;
        private String reason;
        private String operator;
        private String createdAt;
    }

    @Data
    public static class ItemVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String contractNo;
        private Integer lineNo;
        /** 与 contract-client 的 ContractItemDTO.code 对齐 */
        private String code;
        private String materialCode;
        private String materialName;
        private String spec;
        private String unit;
        private BigDecimal qty;
        private BigDecimal price;
        private BigDecimal amount;
        private BigDecimal deliveredQty;
        private String remark;
        private String orgCode;
    }

    @Data
    public static class PaymentPlanVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        /** 付款申请单号 FK-yyyy-XXXXXX */
        private String no;
        private String contractNo;
        private String contractName;
        private String stage;
        private String dueDate;
        private BigDecimal amount;
        /** 付款事由 */
        private String summary;
        private String sourceDocNo;
        /** 关联凭证号（财务域过账回写） */
        private String voucherNo;
        private String status;
        private String orgCode;
        /** 合同与验收凭证 file_id 列表 */
        private java.util.List<String> fileIds;
        /** 合同与验收凭证只读预览 URL */
        private java.util.List<String> attachmentUrls;
        /** 是否临近到期（到期日前 N 天内，N 取埋点 contract.payment.remind-days） */
        private boolean dueSoon;
        /** 是否已逾期且未付款 */
        private boolean overdue;
        /** 提醒天数，随 VO 下发便于前端展示口径 */
        private int remindDays;
    }

    @Data
    public static class PurchasePlanVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String no;
        private String title;
        private String dept;
        private String applicant;
        /** 计划日期（设计稿 date） */
        private String date;
        private String needDate;
        private String status;
        private String contractNo;
        private String orgCode;
        private BigDecimal amount;
        private Integer itemCount;
        private List<PurchasePlanItemVO> items;
        /** 附件 file_id 列表，顺序与 attachmentUrls 一致 */
        private List<String> fileIds;
        /** 附件只读预览 URL（统一文件服务临时签名） */
        private List<String> attachmentUrls;
    }

    /** 发起采购计划的默认申请人/需求部门：服务端按当前账号从 EHR 反查，避免前端雪花 ID 丢精度 */
    @Data
    public static class PurchasePlanProfileVO {
        private String applicant;
        private String dept;
        /** 当前登录人所在部门组织编码：新建/编辑单据 org_code 的服务端权威来源 */
        private String orgCode;
    }

    @Data
    public static class PurchasePlanItemVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String planNo;
        private String materialCode;
        private String materialName;
        private String spec;
        private String unit;
        private BigDecimal qty;
        private BigDecimal price;
        private BigDecimal amount;
    }

    @Data
    public static class AcceptanceVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String no;
        private String contractNo;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long itemId;
        private String materialCode;
        private BigDecimal qty;
        private String date;
        private String result;
        private String status;
        /** 过账凭证号（财务域回写） */
        private String voucherNo;
        private String orgCode;
        /** 验收凭证 file_id 列表，顺序与 attachmentUrls 一致 */
        private List<String> fileIds;
        /** 验收凭证只读预览 URL（统一文件服务临时签名） */
        private List<String> attachmentUrls;
    }

    /** 项目可选清单（供合同侧下拉与可用性校验） */
    @Data
    public static class ProjectOptionVO {
        private String code;
        private String name;
        private String type;
        private String orgCode;
        private String status;
        private BigDecimal budgetAmount;
    }

    /** 项目可用性校验结果 */
    @Data
    public static class ProjectUsableVO {
        private String code;
        private boolean usable;
        private String reason;
    }

    /** 合同执行进度统计 */
    @Data
    public static class ContractStatsVO {
        private long total;
        private BigDecimal amount;
        private BigDecimal paidAmount;
        private BigDecimal inFlightAmount;
        private BigDecimal pendingAmount;
    }
}
