package com.laimengchao.sso.contract.module.contract.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.contract.common.ContractErrorCode;
import com.laimengchao.sso.contract.security.DataScopeService;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 合同域 Service 共用小工具：日期解析、金额归一、组织编码兜底。 */
public final class ContractSupport {

    private ContractSupport() {
    }

    public static LocalDate date(String text, String field) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, field + " 日期格式不合法，应为 yyyy-MM-dd");
        }
    }

    public static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** 业务单据必须带归属组织；前端未传时取当前数据权限根组织的第一个值 */
    public static String requireOrg(String provided, DataScopeService dataScope) {
        String org = text(provided);
        if (!org.isEmpty()) {
            return org;
        }
        String scope = dataScope.currentScopeCode();
        if (scope != null) {
            for (String root : scope.split(",")) {
                if (root != null && !root.trim().isEmpty()) {
                    return root.trim();
                }
            }
        }
        throw new BizException(ContractErrorCode.ORG_REQUIRED);
    }

    /** 主数据允许空 org：优先用传入值，其次用数据权限根，最后留空 */
    public static String optionalOrg(String provided, DataScopeService dataScope) {
        String org = text(provided);
        if (!org.isEmpty()) {
            return org;
        }
        String scope = dataScope.currentScopeCode();
        if (scope != null && !scope.isBlank()) {
            return scope.split(",")[0].trim();
        }
        return "";
    }
}
