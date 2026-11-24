package com.laimengchao.sso.settings.module.notice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 站内消息附件表兜底初始化：与 EHR 的 AnnouncementSchemaInitializer 同思路。
 * 生产环境若把 spring.sql.init.mode 设成 never，schema.sql 不会执行，
 * 这里补一次幂等 CREATE TABLE IF NOT EXISTS，失败只记 error 不阻断启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeAttachmentSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS notice_attachment (
                        id          BIGINT        NOT NULL COMMENT '雪花ID',
                        notice_id   BIGINT        NOT NULL COMMENT '所属站内消息ID（notice_message.id）',
                        file_id     BIGINT        NOT NULL COMMENT '统一文件服务文件ID（file_object.id）',
                        file_name   VARCHAR(255)  NOT NULL DEFAULT '',
                        size        BIGINT        NOT NULL DEFAULT 0,
                        mime        VARCHAR(128)  NOT NULL DEFAULT '',
                        uploaded_by BIGINT        DEFAULT NULL COMMENT '上传账号ID，系统生成的消息可为0',
                        deleted     TINYINT       NOT NULL DEFAULT 0,
                        created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        KEY idx_notice_attach (notice_id, deleted)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内消息附件（统一文件服务）'
                    """);
            log.info("站内消息附件表检查完成：notice_attachment 已存在");
        } catch (Exception e) {
            log.error("站内消息附件表初始化失败（带附件的消息将只发正文）：{}", e.getMessage(), e);
        }
    }
}
