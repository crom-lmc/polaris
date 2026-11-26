package com.laimengchao.sso.contract.common.impl;

import com.laimengchao.sso.contract.common.EmbedPointService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.settings.client.api.EmbedPointApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 合同域埋点读取：开关/阈值统一走 settings 的 EmbedPointApi，不在业务侧存配置表。
 * <p>
 * 埋点缺失、埋点值非法、配置中心短暂不可用都返回调用方给出的内置默认值并记 WARN，
 * 不放大成业务故障。关键链路的默认值由调用方按 fail-closed 语义指定（如超预算默认 false）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbedPointServiceImpl implements EmbedPointService {

    public static final String ACCEPTANCE_REQUIRED_BEFORE_PAY = "contract.acceptance.required-before-pay";
    public static final String PAYMENT_REMIND_DAYS = "contract.payment.remind-days";
    public static final String MATERIAL_CODE_PATTERN = "contract.material.code-pattern";
    public static final String PURCHASE_APPROVAL_AMOUNT = "contract.purchase.approval-amount";
    public static final String SALES_CREDIT_CHECK = "contract.sales.credit-check";
    public static final String CHANGE_MAX_TIMES = "contract.change.max-times";

    private final EmbedPointApi embedPointApi;
    private final ObjectMapper objectMapper;

    /** 采购合同审批升级线（元），默认 100000；非法回退默认 */
    public int purchaseApprovalAmount() {
        int amount = number(PURCHASE_APPROVAL_AMOUNT, 100000);
        return amount >= 0 ? amount : 100000;
    }

    /** 销售合同是否开启信用额度校验，默认 false（保护存量，开启后才拦截） */
    public boolean salesCreditCheck() {
        return bool(SALES_CREDIT_CHECK, false);
    }

    /** 合同变更次数上限，默认 3；小于 0 视为不限 */
    public int changeMaxTimes() {
        return number(CHANGE_MAX_TIMES, 3);
    }

    /** 付款前是否必须完成验收，默认 true（fail-closed，防未验收先付款） */
    public boolean acceptanceRequiredBeforePay() {
        return bool(ACCEPTANCE_REQUIRED_BEFORE_PAY, true);
    }

    /** 付款到期提前提醒天数，默认 7，合法范围 1-365 */
    public int paymentRemindDays() {
        int days = number(PAYMENT_REMIND_DAYS, 7);
        return days >= 1 && days <= 365 ? days : 7;
    }

    /**
     * 物料编码规则埋点。返回 [levels, width, serial]；解析失败或缺失返回 null。
     * 合法性（是否允许改变存量编码结构）由调用方判断，避免埋点误配破坏已有编码。
     */
    public int[] materialCodePattern() {
        String value = raw(MATERIAL_CODE_PATTERN);
        if (value == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (!node.isObject() || !node.has("levels") || !node.has("width") || !node.has("serial")) {
                return null;
            }
            return new int[]{node.get("levels").asInt(), node.get("width").asInt(), node.get("serial").asInt()};
        } catch (Exception e) {
            log.warn("解析合同埋点 {} 的值 {} 失败：{}", MATERIAL_CODE_PATTERN, value, e.getMessage());
            return null;
        }
    }

    /** 读取原始埋点值；读不到返回 null（已记 WARN） */
    public String raw(String code) {
        try {
            var resp = embedPointApi.get(code);
            if (resp == null || !resp.ok() || resp.getData() == null
                    || resp.getData().getValue() == null || resp.getData().getValue().isBlank()) {
                log.warn("读取合同埋点 {} 失败或为空，使用内置默认值", code);
                return null;
            }
            return resp.getData().getValue().trim();
        } catch (Exception e) {
            log.warn("读取合同埋点 {} 异常，使用内置默认值：{}", code, e.getMessage());
            return null;
        }
    }

    /**
     * 布尔开关：兼容裸值 "true"/"false"、JSON 布尔与对象字段三种写法。
     * 任何异常都回落到 defaultValue，调用方按业务语义选择 fail-closed 的默认值。
     */
    public boolean bool(String code, boolean defaultValue) {
        String value = raw(code);
        if (value == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node.isBoolean()) {
                return node.asBoolean();
            }
            if (node.isTextual()) {
                return Boolean.parseBoolean(node.asText());
            }
            if (node.isObject()) {
                String simple = code.substring(code.lastIndexOf('.') + 1);
                JsonNode hit = node.has(simple) ? node.get(simple)
                        : node.has("enabled") ? node.get("enabled") : null;
                if (hit != null) {
                    return hit.asBoolean(defaultValue);
                }
            }
        } catch (Exception e) {
            log.warn("解析合同埋点 {} 的值 {} 失败，使用内置默认值：{}", code, value, e.getMessage());
        }
        return defaultValue;
    }

    /** 整数阈值；缺失或非法返回 defaultValue */
    public int number(String code, int defaultValue) {
        String value = raw(code);
        if (value == null) {
            return defaultValue;
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            return node.isNumber() ? node.asInt() : Integer.parseInt(node.asText().trim());
        } catch (Exception e) {
            log.warn("解析合同埋点 {} 的值 {} 失败，使用内置默认值：{}", code, value, e.getMessage());
            return defaultValue;
        }
    }
}
