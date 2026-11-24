package com.laimengchao.sso.utils;

import java.util.regex.Pattern;

/**
 * 审计入参/出参脱敏：把 query 串、JSON 文本里的敏感键值打码，避免密码/令牌/证件号落库。
 * <p>
 * 纯静态工具，无 Spring 依赖——网关（WebFlux）与业务服务（Servlet）共用同一套规则。
 * 只做「按已知敏感键打码 + 长度截断」，不试图解析复杂结构，失败也不影响原文本可用性。
 */
public final class AuditDataMasker {

    /** 单字段落库上限，与 audit_event.request_params / response_summary 的 varchar 长度留余量 */
    private static final int MAX_LENGTH = 1800;

    private static final String KEYS = "password|passwd|pwd|token|accessToken|refreshToken|secret|authorization|apiKey|idCard|idcard|mobile|phone|bankAccount";

    private static final Pattern JSON_SENSITIVE = Pattern.compile(
            "(?i)(\"(?:" + KEYS + ")\"\\s*:\\s*)\"[^\"]*\"");
    private static final Pattern QUERY_SENSITIVE = Pattern.compile(
            "(?i)((?:^|[?&])(?:" + KEYS + ")=)[^&]*");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*");

    private AuditDataMasker() {
    }

    /**
     * 脱敏并截断。null/空返回空串；敏感键模式匹配到的值统一替换为 ***
     */
    public static String mask(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = JSON_SENSITIVE.matcher(raw).replaceAll("$1\"***\"");
        s = QUERY_SENSITIVE.matcher(s).replaceAll("$1***");
        s = BEARER.matcher(s).replaceAll("$1***");
        return s.length() <= MAX_LENGTH ? s : s.substring(0, MAX_LENGTH) + "...(truncated)";
    }
}
