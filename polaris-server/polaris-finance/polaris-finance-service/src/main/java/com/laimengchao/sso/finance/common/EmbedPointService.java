package com.laimengchao.sso.finance.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.settings.client.api.EmbedPointApi;
import com.laimengchao.sso.settings.client.dto.EmbedPointDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 财务埋点读取器：所有开关/阈值从 settings 的 EmbedPointApi 读，
 * 缺失、配置中心不可用、值非法一律 WARN + 内置默认值，绝不报 ERROR。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedPointService {

    public static final String AUTO_POST = "finance.voucher.auto-post";
    public static final String WRITEOFF_MODE = "finance.receivable.writeoff-mode";
    public static final String ALERT_RATIO = "finance.budget.alert-ratio";
    public static final String AGING_BUCKETS = "finance.aging.buckets";
    public static final String PERIOD_CHECK = "finance.voucher.period-check";
    public static final String CLOSE_DAY = "finance.period.close-day";
    public static final String BUDGET_CONTROL_MODE = "finance.budget.control-mode";
    public static final String EXPENSE_APPROVAL_AMOUNT = "finance.expense.approval-amount";

    private static final boolean DEFAULT_AUTO_POST = false;
    private static final String DEFAULT_WRITEOFF_MODE = "FIFO";
    private static final int DEFAULT_ALERT_RATIO = 80;
    private static final List<Integer> DEFAULT_AGING_BUCKETS = List.of(30, 60, 90, 180, 365);
    private static final boolean DEFAULT_PERIOD_CHECK = true;
    private static final int DEFAULT_CLOSE_DAY = 5;
    private static final String DEFAULT_BUDGET_CONTROL_MODE = "REMIND";
    /** 报销高级审批线缺省：与 settings data.sql 种子一致 */
    private static final int DEFAULT_EXPENSE_APPROVAL_AMOUNT = 5000;

    private final EmbedPointApi embedPointApi;
    private final ObjectMapper objectMapper;

    /** 审批通过自动生成凭证，默认 false（保留手动过账动作） */
    public boolean autoPost() {
        return bool(AUTO_POST, DEFAULT_AUTO_POST);
    }

    /** 核销方式 FIFO / MANUAL，默认 FIFO */
    public String writeoffMode() {
        String mode = text(WRITEOFF_MODE, DEFAULT_WRITEOFF_MODE);
        return "MANUAL".equalsIgnoreCase(mode) ? "MANUAL" : "FIFO";
    }

    /** 预算执行率预警线（%），默认 80 */
    public int alertRatio() {
        int ratio = integer(ALERT_RATIO, DEFAULT_ALERT_RATIO);
        return ratio > 0 && ratio <= 1000 ? ratio : DEFAULT_ALERT_RATIO;
    }

    /** 是否校验凭证期间（默认 true，fail-closed 侧：开启才拦截） */
    public boolean periodCheck() {
        return bool(PERIOD_CHECK, DEFAULT_PERIOD_CHECK);
    }

    /** 次月关账日（默认 5 号），范围 1-28 防止月底日期溢出 */
    public int closeDay() {
        int day = integer(CLOSE_DAY, DEFAULT_CLOSE_DAY);
        return day >= 1 && day <= 28 ? day : DEFAULT_CLOSE_DAY;
    }

    /** 报销高级审批线（元）；金额为负或不可解析时回退默认 5000 */
    public int expenseApprovalAmount() {
        int amount = integer(EXPENSE_APPROVAL_AMOUNT, DEFAULT_EXPENSE_APPROVAL_AMOUNT);
        return amount >= 0 ? amount : DEFAULT_EXPENSE_APPROVAL_AMOUNT;
    }

    /** 预算控制模式 REMIND / STRICT，默认 REMIND（提醒不拦截） */
    public String budgetControlMode() {
        String mode = text(BUDGET_CONTROL_MODE, DEFAULT_BUDGET_CONTROL_MODE);
        return "STRICT".equalsIgnoreCase(mode) ? "STRICT" : "REMIND";
    }

    /** 应收账龄分档上界（天），默认 [30,60,90,180,365] */
    public List<Integer> agingBuckets() {
        JsonNode root = root(AGING_BUCKETS);
        if (root == null) {
            return DEFAULT_AGING_BUCKETS;
        }
        List<Integer> out = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode n : root) {
                if (n.canConvertToInt() && n.asInt() > 0) {
                    out.add(n.asInt());
                }
            }
        } else if (root.isTextual()) {
            for (String part : root.asText().split(",")) {
                try {
                    int v = Integer.parseInt(part.trim());
                    if (v > 0) {
                        out.add(v);
                    }
                } catch (NumberFormatException ignored) {
                    // 单个脏值跳过
                }
            }
        }
        if (out.isEmpty()) {
            log.warn("埋点 {} 未按预期配置，使用内置默认值 {}", AGING_BUCKETS, DEFAULT_AGING_BUCKETS);
            return DEFAULT_AGING_BUCKETS;
        }
        return out;
    }

    // ============ 通用读取（读不到一律 WARN + fallback） ============

    private String text(String code, String fallback) {
        JsonNode root = root(code);
        if (root == null) {
            return fallback;
        }
        String v = root.isTextual() ? root.asText() : root.asText("").trim();
        return v.isEmpty() ? fallback : v;
    }

    private boolean bool(String code, boolean fallback) {
        JsonNode root = root(code);
        if (root == null) {
            return fallback;
        }
        if (root.isBoolean()) {
            return root.asBoolean();
        }
        String v = root.asText("").trim();
        if ("true".equalsIgnoreCase(v)) {
            return true;
        }
        if ("false".equalsIgnoreCase(v)) {
            return false;
        }
        log.warn("埋点 {} 不是布尔值，使用内置默认值 {}", code, fallback);
        return fallback;
    }

    private int integer(String code, int fallback) {
        JsonNode root = root(code);
        if (root == null) {
            return fallback;
        }
        try {
            if (root.isNumber()) {
                return root.asInt();
            }
            return Integer.parseInt(root.asText("").trim());
        } catch (NumberFormatException e) {
            log.warn("埋点 {} 不是数字，使用内置默认值 {}：{}", code, fallback, e.getMessage());
            return fallback;
        }
    }

    private JsonNode root(String code) {
        String raw;
        try {
            var resp = embedPointApi.get(code);
            EmbedPointDTO point = resp == null ? null : resp.getData();
            raw = point == null ? null : point.getValue();
        } catch (Exception e) {
            log.warn("读取财务埋点 {} 失败，使用内置默认值：{}", code, e.getMessage());
            return null;
        }
        if (raw == null || raw.isBlank()) {
            log.warn("财务埋点 {} 缺失或未配置，使用内置默认值", code);
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            // FIFO / REMIND 这类裸字符串不是合法 JSON，按纯文本处理
            try {
                return objectMapper.readTree(objectMapper.writeValueAsString(raw.trim()));
            } catch (Exception ignored) {
                log.warn("财务埋点 {} 解析失败，使用内置默认值：{}", code, e.getMessage());
                return null;
            }
        }
    }
}
