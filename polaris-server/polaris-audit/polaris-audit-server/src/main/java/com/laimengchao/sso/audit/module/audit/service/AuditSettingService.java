package com.laimengchao.sso.audit.module.audit.service;

import com.laimengchao.sso.audit.module.audit.service.impl.AuditSettingServiceImpl;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface AuditSettingService {

    /** 全部配置（默认值 + 库内覆盖），保证前端一次拿到完整结构。 */
    Map<String, String> all();

    String get(String key);

    boolean bool(String key, boolean defaultValue);

    int integer(String key, int defaultValue);

    /** 逗号/换行分隔的列表，去空去重。 */
    List<String> list(String key);

    /** 保存设置：仅接受已知 key，未知 key 忽略，防止污染；写后重载缓存。 */
    void save(Map<String, String> values);

    /** 恢复默认：删除全部覆盖行，回退内置默认值。 */
    void resetAll();

    /** 敏感关键词列表（小写），供接收端二次脱敏。 */
    List<String> sensitiveKeywords();

    /** 路径前缀列表（去尾部通配符，便于 startsWith 比较）。 */
    List<String> ignoredPathPrefixes();

    /** 忽略的应用集合（小写）。 */
    Set<String> ignoredApps();

    /** 忽略的事件类别集合（大写；登录与敏感操作合规必留，强制不加入）。 */
    Set<String> ignoredCategories();

    /** 系统日志最低采集级别（数字越大越严重），低于该级别不上报。 */
    int minLogLevel();

    /** 级别权重：TRACE=10 DEBUG=20 INFO=30 WARN=40 ERROR=50，未知按 WARN。 */
    static int logLevelValue(String level) {
        return AuditSettingServiceImpl.logLevelValue(level);
    }
}
