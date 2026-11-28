package com.laimengchao.sso.contract.common.impl;

import com.laimengchao.sso.contract.common.BizNoService;
import com.laimengchao.sso.contract.common.ContractErrorCode;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.settings.client.api.CodeRuleApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 业务单号统一从配置中心「单号规则」读取（settings.cfg_code_rule），业务侧不自行拼接。
 * <p>
 * 表名必须使用 settings 已播种的 key，否则取号为空；空号直接抛业务错误（fail-closed），
 * 禁止把空串写进业务字段。物料编码不走这里（按父级续位生成，见 MaterialService）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizNoServiceImpl implements BizNoService {

    /** 采购计划 CGJH-yyyy-XXXXXX */
    public static final String TABLE_PURCHASE_PLAN = "contract_purchase_plan";
    /** 采购合同 HT-CG-yyyy-XXXXXX */
    public static final String TABLE_CONTRACT_PURCHASE = "contract_header_purchase";
    /** 销售合同 HT-XS-yyyy-XXXXXX */
    public static final String TABLE_CONTRACT_SALES = "contract_header_sales";
    /** 项目档案 PRJ-yyyyMMdd-XXXXXX */
    public static final String TABLE_PROJECT = "contract_project";
    /** 验收单 JD-yyyyMMdd-XXXXXX */
    public static final String TABLE_ACCEPTANCE = "contract_acceptance";
    public static final String TABLE_SIGN = "contract_sign";
    /** 付款申请单 FK-yyyy-XXXXXX */
    public static final String TABLE_PAYMENT_PLAN = "contract_payment_plan";
    /** 合同变更单 BG-yyyyMMdd-XXXXXX */
    public static final String TABLE_CONTRACT_CHANGE = "contract_change_log";
    /** 往来单位（供应商/客户/两者）GYS-yyyy-XXXXXX */
    public static final String TABLE_PARTNER = "contract_partner";

    private final CodeRuleApi codeRuleApi;

    public String next(String tableName) {
        var res = codeRuleApi.next(tableName);
        String no = res == null || res.getData() == null ? "" : res.getData().trim();
        if (no.isEmpty()) {
            log.warn("取业务单号失败：未配置单号规则或返回为空，tableName={}", tableName);
            throw new BizException(ContractErrorCode.BIZ_NO_EMPTY);
        }
        return no;
    }
}
