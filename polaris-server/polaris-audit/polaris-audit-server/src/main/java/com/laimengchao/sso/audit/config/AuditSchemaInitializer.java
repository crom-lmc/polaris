package com.laimengchao.sso.audit.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 审计库存量结构补齐。
 * <p>
 * schema.sql 的 CREATE TABLE IF NOT EXISTS 对已存在的表不生效，新增字段靠这里幂等补列：
 * 启动时按 information_schema 检测，缺哪列补哪列，重复启动无副作用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!tableExists("audit_event")) {
                return;
            }
            addColumnIfMissing("audit_event", "request_params", "TEXT NULL COMMENT '请求入参（已脱敏）' AFTER detail");
            addColumnIfMissing("audit_event", "http_status", "INT(11) NULL COMMENT 'HTTP 状态码' AFTER request_params");
            addColumnIfMissing("audit_event", "biz_code", "INT(11) NULL COMMENT '业务返回码 R.code' AFTER http_status");
            addColumnIfMissing("audit_event", "response_summary", "VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '响应结果摘要（已脱敏）' AFTER biz_code");
            addColumnIfMissing("audit_event", "cost_ms", "BIGINT(20) NULL COMMENT '处理耗时毫秒' AFTER response_summary");
            log.info("审计库结构检查完成");
        } catch (Exception e) {
            // 迁移失败不阻断启动：新字段为空不影响旧查询，仅新上报缺这几个字段
            log.warn("审计库结构补齐失败（已忽略，请检查数据库权限）：{}", e.getMessage());
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
                Integer.class, table);
        return count != null && count > 0;
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns "
                        + "WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",
                Integer.class, table, column);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("已为审计表 {} 补充字段 {}", table, column);
        }
    }
}
