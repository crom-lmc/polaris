package com.laimengchao.sso.finance.module.finance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.contract.client.api.PartnerApi;
import com.laimengchao.sso.contract.client.dto.PartnerDTO;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 往来单位下拉候选：真相源在合同域，经内部令牌代理给财务前端。
 * <p>
 * 应收应付新增单按类型取客户 / 供应商；只要求登录态，不额外挂权限码——
 * 往来单位是填表基础候选，与合同域单位维护权限是两码事。
 */
@Slf4j
@RestController
@RequestMapping("/api/finance/partners")
@RequiredArgsConstructor
public class PartnerOptionsController {

    private final PartnerApi partnerApi;

    /** role 传 CUSTOMER（应收）/ SUPPLIER（应付），合同域同时纳入 BOTH 角色；失败返回空集不拖垮表单 */
    @GetMapping("/options")
    public R<List<Map<String, Object>>> options(@RequestParam(value = "role", required = false) String role) {
        Long accountId = AccountContext.getAccountId();
        if (accountId == null) {
            return R.ok(Collections.emptyList());
        }
        try {
            com.laimengchao.sso.contract.client.dto.R<List<PartnerDTO>> resp =
                    partnerApi.options(role, "COOPERATING", accountId);
            if (resp != null && resp.ok() && resp.getData() != null) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (PartnerDTO p : resp.getData()) {
                    if (p == null || p.getCode() == null || p.getCode().isBlank()) {
                        continue;
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", p.getCode());
                    m.put("name", p.getName());
                    m.put("role", p.getRole());
                    out.add(m);
                }
                return R.ok(out);
            }
            log.warn("财务读取往来单位下拉失败：{}", resp == null ? "无响应" : resp.getMessage());
        } catch (Exception e) {
            log.warn("财务读取往来单位下拉异常：{}", e.getMessage());
        }
        return R.ok(Collections.emptyList());
    }
}
