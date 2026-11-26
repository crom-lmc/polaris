package com.laimengchao.sso.finance.module.finance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.contract.client.api.ContractApi;
import com.laimengchao.sso.contract.client.dto.ContractDTO;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同下拉候选：真相源在合同域，经内部令牌代理给财务前端。
 * <p>
 * 凭证「关联合同号」只选合同、不在此展示金额；合同台账的 /api/contract/** 带合同域权限码，
 * finance 令牌不可直连，改走 ContractApi 的 /internal/contract/contracts。
 * 接口只要求登录态（/api/finance/** authenticated），不额外挂权限码——合同号是填表基础候选，
 * 与合同本身的查看/管理授权是两码事。
 */
@Slf4j
@RestController
@RequestMapping("/api/finance/contracts")
@RequiredArgsConstructor
public class ContractOptionsController {

    private final ContractApi contractApi;

    /**
     * 合同下拉：value=合同编号，label=编号 · 名称；失败返回空列表，不拖垮表单。
     * 显式带当前账号给合同域解析数据范围——internal Feign 不带 X-Account-Id，
     * 直接调 list 会因无登录上下文被 fail-closed 成空集。
     */
    @GetMapping("/options")
    public R<List<Map<String, Object>>> options() {
        Long accountId = AccountContext.getAccountId();
        if (accountId == null) {
            return R.ok(Collections.emptyList());
        }
        try {
            com.laimengchao.sso.contract.client.dto.R<List<ContractDTO>> resp = contractApi.options(accountId);
            if (resp != null && resp.ok() && resp.getData() != null) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (ContractDTO c : resp.getData()) {
                    if (c == null || c.getNo() == null || c.getNo().isBlank()) {
                        continue;
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("no", c.getNo());
                    m.put("name", c.getName());
                    out.add(m);
                }
                return R.ok(out);
            }
            log.warn("财务读取合同下拉失败：{}", resp == null ? "无响应" : resp.getMessage());
        } catch (Exception e) {
            log.warn("财务读取合同下拉异常：{}", e.getMessage());
        }
        return R.ok(Collections.emptyList());
    }
}
