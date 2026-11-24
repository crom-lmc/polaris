package com.laimengchao.sso.file.config;

import com.laimengchao.sso.file.module.file.service.MultipartUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时清理过期的分片上传会话与残留临时目录（每天凌晨 3 点）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultipartCleanupTask {

    private final MultipartUploadService multipartService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanup() {
        try {
            multipartService.cleanupExpired();
        } catch (Exception e) {
            log.error("分片上传会话定时清理失败：{}", e.getMessage(), e);
        }
    }
}
