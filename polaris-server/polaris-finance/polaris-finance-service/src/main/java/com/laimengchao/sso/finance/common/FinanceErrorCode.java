package com.laimengchao.sso.finance.common;

import com.laimengchao.sso.common.IErrorCode;
import lombok.Getter;

/** 财务域业务错误码（8501+，与其它服务码段不冲突）。 */
@Getter
public enum FinanceErrorCode implements IErrorCode {

    // ---------- 主数据 ----------
    SUBJECT_NOT_FOUND(8501, "会计科目不存在或已删除"),
    BUDGET_SUBJECT_NOT_FOUND(8502, "预算科目不存在或已删除"),
    SUBJECT_IN_USE(8503, "会计科目已被凭证或预算科目引用，禁止删除"),
    BUDGET_SUBJECT_IN_USE(8504, "预算科目已被引用，禁止删除"),
    SUBJECT_CODE_DUPLICATE(8505, "科目编码已存在"),

    // ---------- 单据 ----------
    EXPENSE_NOT_FOUND(8511, "费用报销单不存在或已删除"),
    VOUCHER_NOT_FOUND(8512, "会计凭证不存在或已删除"),
    AR_AP_NOT_FOUND(8513, "应收应付单不存在或已删除"),
    BUDGET_NOT_FOUND(8514, "预算额度不存在或已删除"),
    PAYMENT_NOT_FOUND(8515, "付款单不存在或已删除"),

    // ---------- 状态与流转 ----------
    STATUS_INVALID(8521, "当前单据状态不支持该操作"),
    ALREADY_POSTED(8522, "单据已生成凭证并过账，禁止重复过账"),
    VOUCHER_EXISTS(8523, "业务单据已存在关联凭证"),
    ACCEPTANCE_INVALID(8524, "验收单不存在或验收不合格，不能生成应收"),
    WRITEOFF_AMOUNT_INVALID(8525, "核销金额必须大于零且不超过未核销余额"),
    VOUCHER_PERIOD_CLOSED(8526, "凭证日期落在已关账期间，请调整日期或由管理员调整关账日"),
    BUDGET_STRICT_EXCEEDED(8527, "预算强控模式已开启，当前部门可用额度不足"),
    APPROVAL_START_FAILED(8528, "发起审批失败，请稍后重试"),
    EXPENSE_TYPE_SUBJECT_MISSING(8529, "报销类型未配置会计科目映射，请联系财务在配置中心维护"),
    EXPENSE_ATTACHMENT_REQUIRED(8530, "请上传报销凭证附件"),

    // ---------- 单号与配置 ----------
    NO_GENERATE_FAILED(8531, "业务单号生成失败，请稍后重试"),

    // ---------- 预算与跨服 ----------
    BUDGET_INSUFFICIENT(8541, "预算可用额度不足"),
    BUDGET_AMOUNT_INVALID(8542, "预算调整金额不合法"),
    ACCEPTANCE_FETCH_FAILED(8543, "合同验收单查询失败，请稍后重试");

    private final int code;
    private final String message;

    FinanceErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int httpStatus() {
        return switch (code) {
            case 8501, 8502, 8511, 8512, 8513, 8514, 8515 -> 404;
            case 8521, 8522, 8523, 8524, 8525, 8526, 8527, 8529, 8530, 8503, 8504, 8505, 8541, 8542 -> 400;
            case 8531, 8543, 8528 -> 500;
            default -> 0;
        };
    }
}
