package com.laimengchao.sso.finance.common.impl;

import com.laimengchao.sso.finance.common.BizNoService;
import com.laimengchao.sso.finance.common.FinanceErrorCode;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.settings.client.api.CodeRuleApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 业务单号统一从 settings「单号规则」取号（fail-closed）。
 * <p>
 * 表名即真实业务表名，与配置中心 cfg_code_rule.table_name 一一对应；
 * 取号为空/接口异常一律抛业务错误，绝不把空串写进业务字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizNoServiceImpl implements BizNoService {

    private final CodeRuleApi codeRuleApi;

    /** 费用报销单 BX */
    public static final String TABLE_EXPENSE = "fin_expense";
    /** 会计凭证 PZ（配置中心按月重置） */
    public static final String TABLE_VOUCHER = "fin_voucher";
    /** 应收应付单 YS */
    public static final String TABLE_AR_AP = "fin_ar_ap";
    /** 付款单 FK */
    public static final String TABLE_PAYMENT = "fin_payment";

    /**
     * 取下一个单号。空号/异常直接抛业务错误（fail-closed）。
     */
    public String next(String tableName) {
        String no;
        try {
            var resp = codeRuleApi.next(tableName);
            no = resp == null || resp.getData() == null ? "" : resp.getData().trim();
        } catch (Exception e) {
            log.error("业务单号取号失败（配置中心不可用），tableName={}，原因={}", tableName, e.getMessage());
            throw new BizException(FinanceErrorCode.NO_GENERATE_FAILED);
        }
        if (no.isEmpty()) {
            log.warn("业务单号取号为空（未配置单号规则），tableName={}", tableName);
            throw new BizException(FinanceErrorCode.NO_GENERATE_FAILED);
        }
        return no;
    }

    /**
     * 应付单号：配置中心 fin_ar_ap 规则产出 YS 前缀，按单据类型换成 YF。
     * 流水仍来自取号服务，业务侧只做前缀语义修正，不自行拼号。
     */
    public String nextPayable() {
        String no = next(TABLE_AR_AP);
        return no.startsWith("YS") ? "YF" + no.substring(2) : no;
    }
}
