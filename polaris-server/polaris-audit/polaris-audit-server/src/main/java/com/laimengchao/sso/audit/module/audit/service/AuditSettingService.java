package com.laimengchao.sso.audit.module.audit.service;

import com.laimengchao.sso.audit.module.audit.entity.AuditSetting;
import com.laimengchao.sso.audit.module.audit.mapper.AuditSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审计全局设置：K-V 存取，采集/忽略/留存/脱敏/导出策略集中管理。
 * <p>
 * 各能力点按 key 读取，改动即时生效（内存缓存 + 写后重载）。默认值与现有线上行为一致，
 * 未落库或表不存在时回退默认值，绝不因设置读取失败影响审计主流程。
 * 注意：采集发生在网关/各业务服务一侧，采集总开关的"源头关闭"需 Nacos 下发；
 * 本服务在接收端按这些开关做落库兜底过滤与二次脱敏，保证即使上游仍上报也能按设置收敛。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditSettingService {

    /* ---------- 采集策略 ---------- */
    public static final String COLLECT_GATEWAY_ACCESS = "collect.gatewayAccess";
    public static final String COLLECT_BUSINESS_OP = "collect.businessOp";
    public static final String COLLECT_LOGIN = "collect.login";
    public static final String COLLECT_SYSTEM_LOG = "collect.systemLog";
    public static final String COLLECT_LOG_LEVEL = "collect.logLevel";
    public static final String COLLECT_QUERY_PARAMS = "collect.queryParams";
    public static final String COLLECT_JSON_BODY = "collect.jsonBody";
    public static final String COLLECT_TRUNCATE_LENGTH = "collect.truncateLength";
    public static final String COLLECT_QUEUE_CAPACITY = "collect.queueCapacity";

    /* ---------- 忽略规则 ---------- */
    public static final String IGNORE_PATH_PREFIXES = "ignore.pathPrefixes";
    public static final String IGNORE_APPS = "ignore.apps";
    public static final String IGNORE_CATEGORIES = "ignore.categories";

    /* ---------- 留存与清理 ---------- */
    public static final String RETENTION_EVENT_DAYS = "retention.eventDays";
    public static final String RETENTION_SYSLOG_DAYS = "retention.systemLogDays";
    public static final String RETENTION_CLEAN_TIME = "retention.cleanTime";
    public static final String RETENTION_ARCHIVE_BEFORE = "retention.archiveBeforeClean";

    /* ---------- 脱敏与隐私 ---------- */
    public static final String PRIVACY_SENSITIVE_KEYWORDS = "privacy.sensitiveKeywords";
    public static final String PRIVACY_RECORD_IP = "privacy.recordIp";
    public static final String PRIVACY_RECORD_UA = "privacy.recordUserAgent";
    public static final String PRIVACY_RECORD_OPERATOR_ID = "privacy.recordOperatorId";
    public static final String PRIVACY_LOG_TRUNCATE_LENGTH = "privacy.logTruncateLength";

    /* ---------- 导出与合规 ---------- */
    public static final String EXPORT_ENABLED = "export.enabled";
    public static final String EXPORT_MAX_ROWS = "export.maxRows";
    public static final String EXPORT_CONFIRM = "export.confirmRequired";
    public static final String EXPORT_WATERMARK = "export.watermarkText";
    public static final String EXPORT_RECORD = "export.recordBehavior";

    /** 强制不可关闭：登录事件合规必留。 */
    public static final String CATEGORY_LOGIN = "LOGIN";
    /** 强制不可关闭：敏感操作合规必留。 */
    public static final String CATEGORY_SENSITIVE = "SENSITIVE";

    /** 默认值：与当前线上行为一致，保证启用设置页后行为不突变。顺序即前端展示顺序。 */
    public static final Map<String, String> DEFAULTS;

    static {
        Map<String, String> d = new LinkedHashMap<>();
        d.put(COLLECT_GATEWAY_ACCESS, "true");
        d.put(COLLECT_BUSINESS_OP, "true");
        d.put(COLLECT_LOGIN, "true");
        d.put(COLLECT_SYSTEM_LOG, "true");
        d.put(COLLECT_LOG_LEVEL, "WARN");
        d.put(COLLECT_QUERY_PARAMS, "true");
        d.put(COLLECT_JSON_BODY, "false");
        d.put(COLLECT_TRUNCATE_LENGTH, "1800");
        d.put(COLLECT_QUEUE_CAPACITY, "2000");

        d.put(IGNORE_PATH_PREFIXES, "/actuator/**,/internal/**,/api/file/public/**,/favicon.ico");
        d.put(IGNORE_APPS, "");
        d.put(IGNORE_CATEGORIES, "");

        d.put(RETENTION_EVENT_DAYS, "180");
        d.put(RETENTION_SYSLOG_DAYS, "90");
        d.put(RETENTION_CLEAN_TIME, "03:00");
        d.put(RETENTION_ARCHIVE_BEFORE, "false");

        d.put(PRIVACY_SENSITIVE_KEYWORDS, "password,token,secret,authorization,idcard,mobile");
        d.put(PRIVACY_RECORD_IP, "true");
        d.put(PRIVACY_RECORD_UA, "true");
        d.put(PRIVACY_RECORD_OPERATOR_ID, "true");
        d.put(PRIVACY_LOG_TRUNCATE_LENGTH, "4000");

        d.put(EXPORT_ENABLED, "false");
        d.put(EXPORT_MAX_ROWS, "10000");
        d.put(EXPORT_CONFIRM, "true");
        d.put(EXPORT_WATERMARK, "仅供内部使用 {username} {date}");
        d.put(EXPORT_RECORD, "true");
        DEFAULTS = java.util.Collections.unmodifiableMap(d);
    }

    private final AuditSettingMapper mapper;

    /** 内存缓存，避免高频事件写入时每次查库。 */
    private volatile Map<String, String> cache;

    /** 全部配置（默认值 + 库内覆盖），保证前端一次拿到完整结构。 */
    public Map<String, String> all() {
        return new LinkedHashMap<>(current());
    }

    public String get(String key) {
        return current().get(key);
    }

    public boolean bool(String key, boolean defaultValue) {
        String v = get(key);
        return v == null || v.isBlank() ? defaultValue : Boolean.parseBoolean(v.trim());
    }

    public int integer(String key, int defaultValue) {
        try {
            String v = get(key);
            return v == null || v.isBlank() ? defaultValue : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 逗号/换行分隔的列表，去空去重。 */
    public List<String> list(String key) {
        String v = get(key);
        if (v == null || v.isBlank()) {
            return List.of();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String part : v.split("[,\\n]")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                set.add(p);
            }
        }
        return new ArrayList<>(set);
    }

    /** 保存设置：仅接受已知 key，未知 key 忽略，防止污染；写后重载缓存。 */
    public void save(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        int saved = 0;
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            if (key == null || !DEFAULTS.containsKey(key)) {
                continue;
            }
            String value = e.getValue() == null ? "" : e.getValue();
            if (value.length() > 2000) {
                value = value.substring(0, 2000);
            }
            AuditSetting row = mapper.selectById(key);
            if (row == null) {
                mapper.insert(new AuditSetting(key, value, LocalDateTime.now()));
            } else {
                row.setSettingValue(value);
                row.setUpdatedAt(LocalDateTime.now());
                mapper.updateById(row);
            }
            saved++;
        }
        cache = load();
        log.info("审计全局设置已更新：keys={} changed={}", values.keySet(), saved);
    }

    /** 恢复默认：删除全部覆盖行，回退内置默认值。 */
    public void resetAll() {
        try {
            mapper.delete(null);
            cache = load();
            log.info("审计全局设置已恢复默认");
        } catch (Exception e) {
            log.warn("恢复审计默认设置失败（已忽略）: {}", e.getMessage());
        }
    }

    private Map<String, String> current() {
        Map<String, String> c = cache;
        if (c == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = load();
                }
                c = cache;
            }
        }
        return c;
    }

    private Map<String, String> load() {
        Map<String, String> m = new LinkedHashMap<>(DEFAULTS);
        try {
            for (AuditSetting s : mapper.selectList(null)) {
                if (s != null && s.getSettingKey() != null && DEFAULTS.containsKey(s.getSettingKey())) {
                    m.put(s.getSettingKey(), s.getSettingValue() == null ? "" : s.getSettingValue());
                }
            }
        } catch (Exception e) {
            // 表尚未创建 / 数据库抖动：回退默认值，不影响审计主流程
            log.warn("读取审计全局设置失败，使用默认值: {}", e.getMessage());
        }
        return m;
    }

    /** 敏感关键词列表（小写），供接收端二次脱敏。 */
    public List<String> sensitiveKeywords() {
        List<String> out = new ArrayList<>();
        for (String k : list(PRIVACY_SENSITIVE_KEYWORDS)) {
            out.add(k.toLowerCase());
        }
        return out;
    }

    /** 路径前缀列表（去尾部通配符，便于 startsWith 比较）。 */
    public List<String> ignoredPathPrefixes() {
        List<String> out = new ArrayList<>();
        for (String p : list(IGNORE_PATH_PREFIXES)) {
            String prefix = p;
            int star = prefix.indexOf('*');
            if (star >= 0) {
                prefix = prefix.substring(0, star);
            }
            while (prefix.endsWith("/") && prefix.length() > 1) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            if (!prefix.isBlank()) {
                out.add(prefix);
            }
        }
        return out;
    }

    /** 忽略的应用集合（小写）。 */
    public Set<String> ignoredApps() {
        Set<String> set = new LinkedHashSet<>();
        for (String a : list(IGNORE_APPS)) {
            set.add(a.toLowerCase());
        }
        return set;
    }

    /** 忽略的事件类别集合（大写；登录与敏感操作合规必留，强制不加入）。 */
    public Set<String> ignoredCategories() {
        Set<String> set = new LinkedHashSet<>();
        for (String c : list(IGNORE_CATEGORIES)) {
            String upper = c.trim().toUpperCase();
            if (!CATEGORY_LOGIN.equals(upper) && !CATEGORY_SENSITIVE.equals(upper)) {
                set.add(upper);
            }
        }
        return set;
    }

    /** 系统日志最低采集级别（数字越大越严重），低于该级别不上报。 */
    public int minLogLevel() {
        String level = get(COLLECT_LOG_LEVEL);
        return logLevelValue(level);
    }

    /** 级别权重：TRACE=10 DEBUG=20 INFO=30 WARN=40 ERROR=50，未知按 WARN。 */
    public static int logLevelValue(String level) {
        if (level == null) return 40;
        return switch (level.trim().toUpperCase()) {
            case "TRACE" -> 10;
            case "DEBUG" -> 20;
            case "INFO" -> 30;
            case "ERROR", "FATAL" -> 50;
            default -> 40;
        };
    }
}
