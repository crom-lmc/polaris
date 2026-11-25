package com.laimengchao.sso.file.config;

import com.laimengchao.sso.file.module.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 文件生命周期定时清理：每天凌晨 4 点把超 TTL 文件移入回收站（可恢复）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileLifecycleTask {

    private final FileService fileService;

    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanup() {
        try {
            int count = fileService.expireByTtl();
            if (count > 0) {
                log.info("文件生命周期清理完成，本轮移入回收站 {} 条", count);
            }
            int purged = fileService.purgeExpiredRecycle();
            if (purged > 0) {
                log.info("回收站自动清理完成，本轮物理删除 {} 条", purged);
            }
        } catch (Exception e) {
            log.error("文件生命周期清理失败：{}", e.getMessage(), e);
        }
    }
}
