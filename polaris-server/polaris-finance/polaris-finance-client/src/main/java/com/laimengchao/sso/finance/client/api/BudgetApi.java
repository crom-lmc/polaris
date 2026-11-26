package com.laimengchao.sso.finance.client.api;

import com.laimengchao.sso.finance.client.config.FinanceFeignConfig;
import com.laimengchao.sso.finance.client.dto.BudgetDTO;
import com.laimengchao.sso.finance.client.dto.BudgetOccupyRequest;
import com.laimengchao.sso.finance.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 预算内部接口，直连 finance-service，不经网关。
 */
@FeignClient(
        name = "finance",
        contextId = "finance-budget-client",
        url = "${finance.client.base-url:${FINANCE_URL:http://127.0.0.1:9014}}",
        configuration = FinanceFeignConfig.class)
public interface BudgetApi {

    /** 预算科目主数据（启用中），供合同域选择用；每项含 code/name/year */
    @GetMapping("/internal/finance/budgets/budget-subjects/options")
    R<List<Map<String, Object>>> budgetSubjectOptions(@RequestParam(value = "year", required = false) Integer year);

    /** 部门年度预算可用额；令牌缺失或调用失败时关键链路必须 fail-closed */
    @GetMapping("/internal/finance/budgets/available")
    R<BudgetDTO> available(@RequestParam("dept") String dept,
                           @RequestParam("year") Integer year);

    /** 占用预算（按 bizNo 幂等） */
    @PostMapping("/internal/finance/budgets/occupy")
    R<Boolean> occupy(@RequestBody BudgetOccupyRequest request);

    /** 释放预算（按 bizNo 幂等） */
    @PostMapping("/internal/finance/budgets/release")
    R<Boolean> release(@RequestBody BudgetOccupyRequest request);
}
