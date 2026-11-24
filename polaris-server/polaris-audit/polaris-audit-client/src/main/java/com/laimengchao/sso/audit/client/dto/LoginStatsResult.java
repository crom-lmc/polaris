package com.laimengchao.sso.audit.client.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录统计结果（账号中心概览用）。字段口径与原 sso_login_log 上的聚合 SQL 一致：
 * todayCount=今日成功+失败总数，trend=按天成功/失败数，types=近 30 天登录方式分布。
 */
@Data
public class LoginStatsResult {

    private long todayCount;

    private List<DayStat> trend = List.of();

    private List<TypeStat> types = List.of();

    @Data
    public static class DayStat {
        /** yyyy-MM-dd */
        private String day;
        private long success;
        private long fail;
    }

    @Data
    public static class TypeStat {
        private String key;
        private long cnt;
    }
}
