package com.laimengchao.sso.audit.client.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录统计查询条件。账号中心概览原本直接查 sso_login_log 表，
 * 登录日志迁到 audit-server 后改由本请求取回同一口径的统计结果。
 * <p>
 * accountIds / usernames 为空表示全平台；两者是「或」关系，覆盖失败日志只有用户名没有账号 ID 的情况。
 */
@Data
public class LoginStatsQuery {

    /** 趋势天数 */
    private int days = 14;

    private List<Long> accountIds;

    private List<String> usernames;
}
