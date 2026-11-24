package com.laimengchao.sso.cms.module.media.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 媒体表 file_id 列兜底初始化。
 * schema.sql 的 CREATE TABLE IF NOT EXISTS 对已存在的旧表不会补列，
 * 这里按 forum 的同名机制做一次幂等检查，失败只记日志不阻断启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'cms_media' AND column_name = 'file_id'",
                    Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE cms_media ADD COLUMN file_id bigint(20) DEFAULT NULL "
                        + "COMMENT '统一文件服务文件ID；为空表示仍用旧的本地存储文件'");
                log.info("已为 cms_media 补充 file_id 列");
            }
        } catch (Exception e) {
            log.error("cms_media file_id 列检查失败：{}", e.getMessage(), e);
        }
    }
}
