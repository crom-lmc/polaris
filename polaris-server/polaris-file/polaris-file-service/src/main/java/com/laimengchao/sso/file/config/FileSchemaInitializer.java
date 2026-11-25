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
            // 水印/附件设置：schema.sql 已建表，这里兜底兼容生产 sql.init.mode=never 的场景
            createTableIfMissing("file_watermark_config",
                    "(id BIGINT NOT NULL, biz_type VARCHAR(64) NOT NULL, enabled TINYINT(1) NOT NULL DEFAULT 1, "
                            + "content VARCHAR(512) NOT NULL DEFAULT '', font_size INT NOT NULL DEFAULT 26, "
                            + "color VARCHAR(16) NOT NULL DEFAULT '#8B5CF6', opacity INT NOT NULL DEFAULT 30, "
                            + "rotation INT NOT NULL DEFAULT -24, layout VARCHAR(16) NOT NULL DEFAULT 'TILE', "
                            + "gap INT NOT NULL DEFAULT 46, file_types VARCHAR(64) NOT NULL DEFAULT 'IMAGE,PDF', "
                            + "scenes VARCHAR(64) NOT NULL DEFAULT 'VIEW,SHARE', created_at DATETIME NOT NULL, "
                            + "updated_at DATETIME DEFAULT NULL, PRIMARY KEY(id), UNIQUE KEY uk_wm_biz_type(biz_type)) "
                            + "ENGINE=InnoDB DEFAULT CHARSET=utf8");
            createTableIfMissing("file_attachment_setting",
                    "(setting_key VARCHAR(64) NOT NULL, setting_value VARCHAR(512) NOT NULL DEFAULT '', "
                            + "updated_at DATETIME DEFAULT NULL, PRIMARY KEY(setting_key)) "
                            + "ENGINE=InnoDB DEFAULT CHARSET=utf8");
            log.info("文件服务索引/字段/表检查完成：水印与附件设置表已就绪");
        } catch (Exception e) {
            log.error("文件服务索引初始化失败（秒传去重将退化为全表扫描）：{}", e.getMessage(), e);
        }
    }

    /** 表不存在才 CREATE（幂等，兼容 sql.init.mode=never 的生产库） */
    private void createTableIfMissing(String table, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class, table);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + table + " " + definition);
        log.info("已创建表 {}", table);
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
