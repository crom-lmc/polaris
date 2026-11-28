package com.laimengchao.sso.contract.common;

import com.laimengchao.sso.common.IErrorCode;
import lombok.Getter;

/** 合同服务业务码段（8401+，与 meeting 8301+ / finance 8501+ 不冲突）。 */
@Getter
public enum ContractErrorCode implements IErrorCode {

    PARTNER_NOT_FOUND(8401, "往来单位不存在或已删除"),
    PARTNER_CODE_DUPLICATE(8402, "往来单位编码已存在"),
    PARTNER_IN_USE(8403, "往来单位已被合同引用，禁止删除"),
    PARTNER_BANK_FORBIDDEN(8404, "无权查看银行账号"),

    MATERIAL_NOT_FOUND(8405, "物料节点不存在或已删除"),
    MATERIAL_CODE_INVALID(8406, "编码层级不合法：分类最多三级，物料必须挂在三级分类下"),
    MATERIAL_IN_USE(8407, "物料已被合同清单或采购计划引用，禁止删除"),
    MATERIAL_HAS_CHILDREN(8408, "分类下仍有子节点，禁止删除"),

    PROJECT_NOT_FOUND(8409, "项目档案不存在或已删除"),
    PROJECT_NOT_USABLE(8410, "项目已结项/取消/暂停，不能关联新单据"),
    PROJECT_STATUS_INVALID(8411, "项目状态不支持该流转"),

    CONTRACT_NOT_FOUND(8412, "合同不存在或已删除"),
    CONTRACT_STATUS_INVALID(8413, "合同状态不支持该操作"),
    ITEM_NOT_FOUND(8414, "合同清单行不存在或已删除"),
    BUDGET_EXCEEDED(8415, "合同金额超过项目预算，清单保存已拦截"),

    PAYMENT_PLAN_NOT_FOUND(8416, "付款计划不存在或已删除"),
    PLAN_NOT_FOUND(8417, "采购计划不存在或已删除"),
    PLAN_STATUS_INVALID(8418, "采购计划状态不支持该操作"),
    ACCEPTANCE_NOT_FOUND(8419, "验收单不存在或已删除"),
    ACCEPTANCE_QTY_INVALID(8420, "验收数量不合法：不能超过清单未交付数量"),
    ACCEPTANCE_STATUS_INVALID(8421, "验收单状态不支持该操作"),
    ACCEPTANCE_REQUIRED(8424, "埋点要求付款前必须完成验收，当前合同无合格验收单"),
    BIZ_NO_EMPTY(8422, "单号规则未配置或返回为空"),
    ORG_REQUIRED(8423, "归属组织不能为空且无法从数据权限推导"),
    APPROVAL_START_FAILED(8425, "发起审批失败，请稍后重试"),
    CREDIT_LIMIT_EXCEEDED(8426, "销售合同金额超出客户授信额度，请调整金额或提高授信"),
    CHANGE_TIMES_EXCEEDED(8427, "合同变更次数已达埋点上限，不能再变更"),
    MATERIAL_STATUS_INVALID(8428, "分类状态不支持该操作"),
    ATTACHMENT_UPLOAD_FAILED(8429, "附件上传失败，请稍后重试"),
    ACCEPTANCE_DATE_REQUIRED(8430, "验收日期不能为空"),
    PAYMENT_TERM_NOT_FOUND(8431, "付款节点不存在或已删除"),
    PAYMENT_TERM_LIMIT(8432, "超出该付款节点可申请金额"),
    WARRANTY_NOT_DUE(8433, "质保期未满，暂不能申请质保金"),
    GOODS_ITEMS_REQUIRED(8434, "请选择到货清单行"),
    AR_PUSH_FAILED(8435, "推应收失败，验收已保留，可稍后重试"),
    PAYMENT_TERM_REQUIRED(8436, "请选择合同付款节点"),
    SIGN_NOT_FOUND(8437, "签收单不存在或已删除"),
    SIGN_QTY_INVALID(8438, "签收数量不合法：不能超过清单未签收数量"),
    SIGN_DATE_REQUIRED(8439, "签收日期不能为空"),
    SIGN_REQUIRED(8440, "所选清单行尚未到货签收");

    private final int code;
    private final String message;

    ContractErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int httpStatus() {
        return switch (code) {
            case 8401, 8405, 8409, 8412, 8414, 8416, 8417, 8419, 8431 -> 404;
            case 8404 -> 403;
            case 8422, 8425 -> 503;
            default -> 400;
        };
    }
}
