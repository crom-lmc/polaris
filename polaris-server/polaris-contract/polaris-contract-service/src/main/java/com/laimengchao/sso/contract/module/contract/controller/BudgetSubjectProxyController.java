package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.client.api.BudgetApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 预算科目主数据代理：真相源在财务域 fin_budget_subject，经内部令牌 Feign 读取。
 * <p>
 * 合同域采购计划的「预算科目」只能从主数据选，不允许手输；finance 的
 * /api/finance/budget-subjects/options 带 @Permit(finance:*)，contract 令牌不可直连，
 * 故走 /internal/finance/** + BudgetApi 代理成本域接口。
 * 只要求登录态（/api/contract/** authenticated），失败返回空列表不拖垮表单。
 */
@Slf4j
@RestController
@RequestMapping("/api/contract/budget-subjects")
@RequiredArgsConstructor
public class BudgetSubjectProxyController {

    private final BudgetApi budgetApi;

    /** 启用中的预算科目（code/name/year），供采购计划下拉 */
    @GetMapping("/options")
    public R<List<Map<String, Object>>> options(
            @RequestParam(value = "year", required = false) Integer year) {
        try {
            com.laimengchao.sso.finance.client.dto.R<List<Map<String, Object>>> resp = budgetApi.budgetSubjectOptions(year);
            if (resp != null && resp.ok() && resp.getData() != null) {
                return R.ok(resp.getData());
            }
            log.warn("合同读取财务预算科目主数据失败：{}", resp == null ? "无响应" : resp.getMessage());
        } catch (Exception e) {
            log.warn("合同读取财务预算科目主数据异常：{}", e.getMessage());
        }
        return R.ok(Collections.emptyList());
    }
}
