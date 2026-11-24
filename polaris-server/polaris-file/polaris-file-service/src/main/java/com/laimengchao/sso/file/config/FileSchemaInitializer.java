package com.laimengchao.sso.file.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 文件服务索引兜底初始化。
 * <p>
 * schema.sql 用 CREATE TABLE IF NOT EXISTS，对已存在的表不会补索引。秒传去重依赖
 * file_object(sha256, size, status) 组合索引，这里在启动时按 information_schema 判断，
 * 缺则幂等 ALTER；失败只记 error 不阻断启动（最坏退化为全表扫描，功能仍可用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            addIndexIfMissing("file_object", "idx_sha_dedup", "(sha256, size, status)");
            addColumnIfMissing("file_storage_policy", "quota_gb", "BIGINT NOT NULL DEFAULT 0 COMMENT '业务总配额GB，0不限'");
            addColumnIfMissing("file_storage_policy", "ttl_days", "INT NOT NULL DEFAULT 0 COMMENT '生命周期天数，0永久'");
            log.info("文件服务索引/字段检查完成：idx_sha_dedup / quota_gb / ttl_days 已就绪");
        } catch (Exception e) {
            log.error("文件服务索引初始化失败（秒传去重将退化为全表扫描）：{}", e.getMessage(), e);
        }
    }

    /** 列不存在才执行 ALTER（幂等，兼容存量库） */
    private void addColumnIfMissing(String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        log.info("已为表 {} 补充字段 {}", table, column);
    }

    /** 索引不存在才执行 ALTER，避免每次启动都改表 */
    private void addIndexIfMissing(String table, String index, String columns) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, table, index);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD INDEX " + index + " " + columns);
        log.info("已为表 {} 补充索引 {}", table, index);
    }
}
