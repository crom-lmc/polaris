package com.laimengchao.sso.audit.module.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.audit.module.audit.entity.SystemLog;
import com.laimengchao.sso.audit.module.audit.mapper.SystemLogMapper;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.logging.SystemLogEvent;
import com.laimengchao.sso.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 系统运行日志写入与查询。写入失败只告警不抛错——日志采集绝不能影响上报服务或本服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final SystemLogMapper mapper;
    private final AuditSettingService settings;

    /** 批量写入，逐条转换；单条异常不影响同批其他记录。 */
    public void recordBatch(List<SystemLogEvent> events) {
        if (events == null || events.isEmpty()) return;
        // 系统日志采集总开关（接收端兜底）；审计服务自身强制不采集，防递归
        boolean enabled = settings.bool(AuditSettingService.COLLECT_SYSTEM_LOG, true);
        int minLevel = settings.minLogLevel();
        int truncate = settings.integer(AuditSettingService.PRIVACY_LOG_TRUNCATE_LENGTH, 4000);
        int failed = 0;
        int skipped = 0;
        for (SystemLogEvent e : events) {
            if (!enabled || e == null || AuditSettingService.logLevelValue(e.getLevel()) < minLevel) {
                skipped++;
                continue;
            }
            try {
                mapper.insert(toEntity(e, truncate));
            } catch (Exception ex) {
                failed++;
                log.warn("写入系统日志失败（已忽略）: app={} logger={} reason={}",
                        e.getApp(), e.getLogger(), ex.getMessage());
            }
        }
        // 批次级汇总（不逐条刷屏）；本服务自身采集已关闭，这些 INFO 不会回流到 system_log
        if (failed > 0) {
            log.warn("系统日志批量写入存在失败：total={} success={} failed={} skipped={}",
                    events.size(), events.size() - failed - skipped, failed, skipped);
        } else {
            log.info("系统日志批量写入成功：total={} skipped={}", events.size(), skipped);
        }
    }

    private SystemLog toEntity(SystemLogEvent e, int truncate) {
        int limit = Math.max(200, Math.min(truncate, 3900)); // 留余量，不超过字段 toast
        SystemLog row = new SystemLog();
        row.setId(IdUtils.nextId());
        row.setApp(trim(e.getApp(), 64));
        row.setLevel(trim(e.getLevel(), 16).toUpperCase());
        row.setLogger(trim(e.getLogger(), 255));
        row.setThread(trim(e.getThread(), 128));
        row.setMessage(trim(e.getMessage(), limit));
        row.setStackTrace(trim(e.getStackTrace(), limit));
        long ts = e.getTimestamp() > 0 ? e.getTimestamp() : System.currentTimeMillis();
        row.setCreatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()));
        return row;
    }

    /** 按应用/级别/关键字/日期分页查询。 */
    public PageResult<SystemLog> page(int page, int size, String app, String level,
                                      String keyword, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<SystemLog> w = new LambdaQueryWrapper<>();
        if (notBlank(app)) w.eq(SystemLog::getApp, app.trim());
        if (notBlank(level)) w.eq(SystemLog::getLevel, level.trim().toUpperCase());
        if (notBlank(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(SystemLog::getMessage, k)
                    .or().like(SystemLog::getLogger, k)
                    .or().like(SystemLog::getStackTrace, k));
        }
        if (startDate != null) w.ge(SystemLog::getCreatedAt, startDate.atStartOfDay());
        if (endDate != null) w.lt(SystemLog::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        w.orderByDesc(SystemLog::getCreatedAt).orderByDesc(SystemLog::getId);
        IPage<SystemLog> p = mapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    /** 下拉框用：最近 30 天出现过的应用列表（按名称排序）。 */
    public List<String> apps() {
        List<String> rows = mapper.selectObjs(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SystemLog>()
                .select("DISTINCT app")
                .ge("created_at", LocalDateTime.now().minusDays(30))
                .orderByAsc("app"));
        List<String> out = new ArrayList<>();
        for (Object r : rows) {
            if (r != null && !String.valueOf(r).isBlank()) out.add(String.valueOf(r));
        }
        return out;
    }

    /** 概览统计：今日/累计条数、ERROR/WARN 数、按应用与级别分布。 */
    public java.util.Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("today", mapper.selectCount(new LambdaQueryWrapper<SystemLog>()
                .ge(SystemLog::getCreatedAt, today.atStartOfDay())));
        out.put("total", mapper.selectCount(new LambdaQueryWrapper<>()));
        out.put("error", mapper.selectCount(new LambdaQueryWrapper<SystemLog>()
                .eq(SystemLog::getLevel, "ERROR")));
        out.put("warn", mapper.selectCount(new LambdaQueryWrapper<SystemLog>()
                .eq(SystemLog::getLevel, "WARN")));
        List<java.util.Map<String, Object>> byApp = mapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SystemLog>()
                        .select("app, COUNT(*) AS cnt")
                        .ge("created_at", today.minusDays(29).atStartOfDay())
                        .groupBy("app").orderByDesc("cnt"));
        out.put("byApp", byApp);
        List<java.util.Map<String, Object>> byLevel = mapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SystemLog>()
                        .select("level, COUNT(*) AS cnt")
                        .ge("created_at", today.minusDays(29).atStartOfDay())
                        .groupBy("level").orderByDesc("cnt"));
        out.put("byLevel", byLevel);
        return out;
    }

    /**
     * 日志文件列表：按「应用 + 日期」分组统计，前端据此列出"某应用某天的日志"，
     * 点开再走 stream 拉当天全文。按日期/应用过滤，且只统计有数据的分组。
     */
    public List<Map<String, Object>> logFiles(String app, LocalDate startDate, LocalDate endDate) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SystemLog> q =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SystemLog>()
                        .select("app, DATE(created_at) AS day, COUNT(*) AS cnt, "
                                + "SUM(CASE WHEN level='ERROR' THEN 1 ELSE 0 END) AS err, "
                                + "SUM(CASE WHEN level='WARN' THEN 1 ELSE 0 END) AS warn_, "
                                + "MIN(created_at) AS first_at, MAX(created_at) AS last_at")
                        .groupBy("app, DATE(created_at)");
        if (notBlank(app)) q.eq("app", app.trim());
        if (startDate != null) q.ge("created_at", startDate.atStartOfDay());
        if (endDate != null) q.lt("created_at", endDate.plusDays(1).atStartOfDay());
        q.orderByDesc("day").orderByAsc("app");
        List<Map<String, Object>> rows = mapper.selectMaps(q);
        // 别名 warn_ 转 warn，避免与 SQL 保留字冲突
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new java.util.LinkedHashMap<>(r);
            m.put("warn", m.remove("warn_"));
            out.add(m);
        }
        return out;
    }

    /**
     * 终端日志流：按应用 + 日期拉当天全部日志，时间正序（与日志文件一致），
     * 支持级别/关键字过滤与最大行数限制，避免一次性把整天日志全吐给前端。
     */
    public List<SystemLog> stream(String app, LocalDate day, String level, String keyword, int limit) {
        LambdaQueryWrapper<SystemLog> w = new LambdaQueryWrapper<>();
        if (notBlank(app)) w.eq(SystemLog::getApp, app.trim());
        if (day != null) {
            w.ge(SystemLog::getCreatedAt, day.atStartOfDay());
            w.lt(SystemLog::getCreatedAt, day.plusDays(1).atStartOfDay());
        }
        if (notBlank(level)) w.eq(SystemLog::getLevel, level.trim().toUpperCase());
        if (notBlank(keyword)) {
            String k = keyword.trim();
            w.and(q -> q.like(SystemLog::getMessage, k)
                    .or().like(SystemLog::getLogger, k)
                    .or().like(SystemLog::getStackTrace, k));
        }
        // 先按时间倒序取最近 limit 条，再翻转成正序（与日志文件从上到下的顺序一致）
        w.orderByDesc(SystemLog::getCreatedAt).orderByDesc(SystemLog::getId);
        int safeLimit = Math.max(1, Math.min(limit, 5000));
        IPage<SystemLog> p = mapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<SystemLog>(1, safeLimit), w);
        List<SystemLog> ordered = new ArrayList<>(p.getRecords());
        java.util.Collections.reverse(ordered);
        return ordered;
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private String trim(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
