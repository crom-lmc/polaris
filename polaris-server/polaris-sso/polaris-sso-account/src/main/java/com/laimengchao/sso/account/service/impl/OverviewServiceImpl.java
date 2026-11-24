package com.laimengchao.sso.account.service.impl;

import com.laimengchao.sso.account.dto.response.OverviewVO;
import com.laimengchao.sso.account.integration.EhrOrgClient;
import com.laimengchao.sso.account.mapper.OverviewMapper;
import com.laimengchao.sso.account.service.OverviewService;
import com.laimengchao.sso.audit.client.api.AuditApi;
import com.laimengchao.sso.audit.client.dto.LoginStatsQuery;
import com.laimengchao.sso.audit.client.dto.LoginStatsResult;
import com.laimengchao.sso.audit.client.dto.R;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 账号中心概览统计实现：账号自身数字走本库，登录相关数字走 audit-server。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverviewServiceImpl implements OverviewService {

    private static final int TREND_DAYS = 14;
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 账号状态字典：与 sso_account.status 注释一致 */
    private static final Map<Integer, String> STATUS_NAMES = new LinkedHashMap<>();
    /** 登录方式字典：给出友好名，未知类型回退原始码 */
    private static final Map<String, String> LOGIN_TYPE_NAMES = new LinkedHashMap<>();

    static {
        STATUS_NAMES.put(1, "正常");
        STATUS_NAMES.put(2, "禁用");
        STATUS_NAMES.put(3, "锁定");
        STATUS_NAMES.put(4, "未激活");
        LOGIN_TYPE_NAMES.put("password", "密码登录");
        LOGIN_TYPE_NAMES.put("email", "邮箱登录");
        LOGIN_TYPE_NAMES.put("wechat", "微信登录");
    }

    private final OverviewMapper mapper;
    private final EhrOrgClient ehrOrgClient;
    private final AuditApi auditApi;

    @Override
    public OverviewVO overview() {
        String scopeCode = AccountContext.getDataScopeOrgCode();
        if (scopeCode != null && !scopeCode.isBlank()) {
            // 与账户列表同一口径：先把数据权限根组织反查成账号集合，后续所有卡片只统计这个集合。
            // EHR 不可用时反查返回空集，宁可展示 0 也不能退回全平台数据。
            return scopedOverview(scopedAccountIds(scopeCode));
        }
        return globalOverview();
    }

    /** data_scope_org_code 可能有多个组织根（逗号分隔），逐个反查后去重 */
    private List<Long> scopedAccountIds(String scopeCode) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String root : scopeCode.split(",")) {
            String code = root.trim();
            if (!code.isEmpty()) ids.addAll(ehrOrgClient.accountIdsByCode(code));
        }
        return new ArrayList<>(ids);
    }

    /** 未配置数据权限：保持原来的全平台口径 */
    private OverviewVO globalOverview() {
        OverviewVO vo = new OverviewVO();
        vo.setAccountCount(mapper.countAccounts());
        vo.setAppCount(mapper.countApps());
        vo.setRoleCount(mapper.countRoles());
        vo.setPermissionCount(mapper.countPermissions());
        LoginStatsResult stats = loginStats(null, null);
        vo.setTodayLoginCount(stats.getTodayCount());
        vo.setMonthNewAccountCount(mapper.countMonthNewAccounts());
        vo.setLoginTrend(buildTrend(stats.getTrend()));
        vo.setAccountStatus(buildDistribution(mapper.accountStatus(), STATUS_NAMES));
        vo.setLoginTypes(buildTypeDistribution(stats.getTypes()));
        return vo;
    }

    /** 已配置数据权限：账号/应用/角色/权限/登录全部收敛到同一个账号集合 */
    private OverviewVO scopedOverview(List<Long> accountIds) {
        OverviewVO vo = emptyOverview();
        if (accountIds == null || accountIds.isEmpty()) return vo;

        List<String> usernames = mapper.usernamesByIds(accountIds);
        vo.setAccountCount(mapper.countScopedAccounts(accountIds));
        vo.setAppCount(mapper.countScopedApps(accountIds));
        vo.setRoleCount(mapper.countScopedRoles(accountIds));
        vo.setPermissionCount(mapper.countScopedPermissions(accountIds));
        LoginStatsResult stats = loginStats(accountIds, usernames);
        vo.setTodayLoginCount(stats.getTodayCount());
        vo.setMonthNewAccountCount(mapper.countScopedMonthNewAccounts(accountIds));
        vo.setLoginTrend(buildTrend(stats.getTrend()));
        vo.setAccountStatus(buildDistribution(mapper.scopedAccountStatus(accountIds), STATUS_NAMES));
        vo.setLoginTypes(buildTypeDistribution(stats.getTypes()));
        return vo;
    }

    /** 登录统计是旁路数据：审计中心不可用时展示 0，不让账号概览整页失败。 */
    private LoginStatsResult loginStats(List<Long> accountIds, List<String> usernames) {
        try {
            LoginStatsQuery query = new LoginStatsQuery();
            query.setDays(TREND_DAYS);
            query.setAccountIds(accountIds);
            query.setUsernames(usernames);
            R<LoginStatsResult> res = auditApi.loginStats(query);
            return res != null && res.getData() != null ? res.getData() : new LoginStatsResult();
        } catch (Exception e) {
            log.warn("获取登录统计失败（已降级为 0）: {}", e.getMessage());
            return new LoginStatsResult();
        }
    }

    /** 空结果也要给前端空数组，避免图表计算拿到 null */
    private OverviewVO emptyOverview() {
        OverviewVO vo = new OverviewVO();
        vo.setLoginTrend(List.of());
        vo.setAccountStatus(List.of());
        vo.setLoginTypes(List.of());
        return vo;
    }

    /** 近 14 天按日期升序，缺勤日补 0 */
    private List<OverviewVO.LoginTrendItem> buildTrend(List<LoginStatsResult.DayStat> rows) {
        Map<String, LoginStatsResult.DayStat> byDay = new LinkedHashMap<>();
        if (rows != null) {
            for (LoginStatsResult.DayStat row : rows) {
                byDay.put(row.getDay(), row);
            }
        }
        List<OverviewVO.LoginTrendItem> items = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            String day = today.minusDays(i).format(DAY_FORMAT);
            LoginStatsResult.DayStat value = byDay.get(day);
            items.add(new OverviewVO.LoginTrendItem(
                    day,
                    value == null ? 0 : value.getSuccess(),
                    value == null ? 0 : value.getFail()));
        }
        return items;
    }

    /** 分组行 -> 分布项；按字典顺序输出，值恒 >0 才有意义 */
    private List<OverviewVO.DistributionItem> buildDistribution(
            List<Map<String, Object>> rows, Map<?, String> nameDict) {
        List<OverviewVO.DistributionItem> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("key"));
            long cnt = number(row.get("cnt"));
            if (cnt <= 0) continue;
            items.add(new OverviewVO.DistributionItem(nameOf(key, nameDict), cnt));
        }
        return items;
    }

    private List<OverviewVO.DistributionItem> buildTypeDistribution(List<LoginStatsResult.TypeStat> rows) {
        List<OverviewVO.DistributionItem> items = new ArrayList<>();
        if (rows == null) return items;
        for (LoginStatsResult.TypeStat row : rows) {
            if (row.getCnt() <= 0) continue;
            String key = row.getKey() == null ? "" : row.getKey();
            items.add(new OverviewVO.DistributionItem(nameOf(key, LOGIN_TYPE_NAMES), row.getCnt()));
        }
        return items;
    }

    private String nameOf(String key, Map<?, String> dict) {
        // 状态库的 key 是数值，登录方式是字符串，分别尝试两种取值
        String byInt = dict.get(parseIntSafe(key));
        if (byInt != null) return byInt;
        String byStr = dict.get(key);
        return byStr != null ? byStr : key;
    }

    private Integer parseIntSafe(String key) {
        try {
            return Integer.valueOf(key);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return 0;
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
