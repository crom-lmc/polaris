package com.laimengchao.sso.ehr.module.attachment.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 附件表 file_id 列兜底初始化（与 forum/cms 同机制）。
 * 注意：这里的 file_id 是统一文件服务文件ID，业务表里的 file_id 存的是 ehr_attachment.id，语义不同。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = 'ehr_attachment' AND column_name = 'file_id'",
                    Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE ehr_attachment ADD COLUMN file_id bigint(20) DEFAULT NULL "
                        + "COMMENT '统一文件服务文件ID；为空表示仍用旧的本地存储文件'");
                log.info("已为 ehr_attachment 补充 file_id 列");
            }
        } catch (Exception e) {
            log.error("ehr_attachment file_id 列检查失败：{}", e.getMessage(), e);
        }
    }
}
