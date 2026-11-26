package com.laimengchao.sso.audit.module.audit.service;

import com.laimengchao.sso.audit.module.audit.entity.SystemLog;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.logging.SystemLogEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SystemLogService {

    /** 批量写入，逐条转换；单条异常不影响同批其他记录。 */
    void recordBatch(List<SystemLogEvent> events);

    /** 按应用/级别/关键字/日期分页查询。 */
    PageResult<SystemLog> page(int page, int size, String app, String level, String keyword, LocalDate startDate, LocalDate endDate);

    /** 下拉框用：最近 30 天出现过的应用列表（按名称排序）。 */
    List<String> apps();

    /** 概览统计：今日/累计条数、ERROR/WARN 数、按应用与级别分布。 */
    java.util.Map<String, Object> overview();

    /**
     * 日志文件列表：按「应用 + 日期」分组统计，前端据此列出"某应用某天的日志"，
     * 点开再走 stream 拉当天全文。按日期/应用过滤，且只统计有数据的分组。
     */
    List<Map<String, Object>> logFiles(String app, LocalDate startDate, LocalDate endDate);

    /**
     * 终端日志流：按应用 + 日期拉当天全部日志，时间正序（与日志文件一致），
     * 支持级别/关键字过滤与最大行数限制，避免一次性把整天日志全吐给前端。
     */
    List<SystemLog> stream(String app, LocalDate day, String level, String keyword, int limit);
}
