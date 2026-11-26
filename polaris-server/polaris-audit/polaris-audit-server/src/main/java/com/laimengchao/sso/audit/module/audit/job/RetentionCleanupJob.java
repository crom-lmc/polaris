package com.laimengchao.sso.audit.module.audit.job;

import com.laimengchao.sso.audit.module.audit.service.impl.AuditSettingServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.audit.module.audit.entity.AuditEvent;
import com.laimengchao.sso.audit.module.audit.entity.SystemLog;
import com.laimengchao.sso.audit.module.audit.mapper.AuditEventMapper;
import com.laimengchao.sso.audit.module.audit.mapper.SystemLogMapper;
import com.laimengchao.sso.audit.module.audit.service.AuditSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 留存清理任务：按全局设置保留天数批量删除过期的审计事件与系统日志。
 * <p>
 * 每天按 retention.cleanTime（默认 03:00）执行；单次批量上限避免长事务，循环删至无更多过期记录。
 * 清理本身写本地日志（审计服务自身不采集 system_log，防递归）；archiveBeforeClean 为预留开关，
 * 真实归档需对接文件存储位置，当前仅记录告警，不影响清理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionCleanupJob {

    private static final int BATCH = 1000;
    private static final long MAX_ROUNDS = 200; // 单次调度最多删 20w 行，防止一次清理卡死

    private final AuditEventMapper eventMapper;
    private final SystemLogMapper logMapper;
    private final AuditSettingService settings;

    /** 每分钟检查一次是否到达设定的清理时间（HH:mm），命中当天首次执行。 */
    @Scheduled(cron = "0 * * * * ?")
    public void checkAndRun() {
        String cleanTime = settings.get(AuditSettingServiceImpl.RETENTION_CLEAN_TIME);
        if (cleanTime == null || !cleanTime.matches("\\d{1,2}:\\d{2}")) {
            return;
        }
        String[] parts = cleanTime.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        LocalDateTime now = LocalDateTime.now();
        if (now.getHour() != hour || now.getMinute() != minute) {
            return;
        }
        // 用分钟级标记避免同一分钟内重复执行（Scheduled 每秒不触发，这里 cron 每分钟一次，天然单次）
        runCleanup();
    }

    /** 执行清理，供手动触发或测试调用。 */
    public void runCleanup() {
        int eventDays = settings.integer(AuditSettingServiceImpl.RETENTION_EVENT_DAYS, 180);
        int logDays = settings.integer(AuditSettingServiceImpl.RETENTION_SYSLOG_DAYS, 90);
        boolean archive = settings.bool(AuditSettingServiceImpl.RETENTION_ARCHIVE_BEFORE, false);
        if (archive) {
            log.warn("审计留存清理开启归档模式，但归档存储尚未对接，本次仍直接删除（请评估数据不可恢复风险）");
        }
        LocalDateTime eventBefore = LocalDateTime.now().minusDays(Math.max(1, eventDays));
        LocalDateTime logBefore = LocalDateTime.now().minusDays(Math.max(1, logDays));

        long deletedEvents = deleteBatches(eventBefore, true);
        long deletedLogs = deleteBatches(logBefore, false);
        log.info("审计留存清理完成：删除过期审计事件={} 系统日志={} eventBefore={} logBefore={}",
                deletedEvents, deletedLogs, eventBefore, logBefore);
    }

    /** 按批次循环删除，返回删除总数。 */
    private long deleteBatches(LocalDateTime before, boolean event) {
        long total = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            int n;
            if (event) {
                n = eventMapper.delete(new LambdaQueryWrapper<AuditEvent>()
                        .lt(AuditEvent::getCreatedAt, before).last("LIMIT " + BATCH));
            } else {
                n = logMapper.delete(new LambdaQueryWrapper<SystemLog>()
                        .lt(SystemLog::getCreatedAt, before).last("LIMIT " + BATCH));
            }
            total += n;
            if (n < BATCH) {
                break;
            }
        }
        return total;
    }
}
