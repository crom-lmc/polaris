package com.laimengchao.sso.openapi.module.log.service;

import com.laimengchao.sso.openapi.module.log.vo.LogQuery;

import java.util.Map;

public interface StatService {

    /** 总览：调用量/成功/失败/成功率/平均耗时/P95/应用数 + 失败 TOP API + 按天趋势。 */
    Map<String, Object> overview(LogQuery q);

    /** 按天调用趋势，支持独立时间范围（概览卡片默认最近 10 天，弹窗可自定义）。 */
    java.util.List<Map<String, Object>> trend(String from, String to);

    /** 失败 TOP API，limit 做上限保护。 */
    java.util.List<Map<String, Object>> topFailed(String from, String to, int limit);

    /** 最近异常，limit 做上限保护。 */
    java.util.List<Map<String, Object>> recentErrors(String from, String to, int limit);
}
