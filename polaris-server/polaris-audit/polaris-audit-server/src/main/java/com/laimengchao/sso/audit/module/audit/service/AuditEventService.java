package com.laimengchao.sso.audit.module.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.audit.client.dto.AuditEventRequest;
import com.laimengchao.sso.audit.client.dto.LoginStatsQuery;
import com.laimengchao.sso.audit.client.dto.LoginStatsResult;
import com.laimengchao.sso.audit.module.audit.entity.AuditEvent;
import com.laimengchao.sso.audit.module.audit.mapper.AuditEventMapper;
import com.laimengchao.sso.audit.module.audit.vo.LoginEventVO;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.utils.IdUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台统一审计事件写入与查询。
 * <p>
 * 写入失败只告警不抛错——审计不能让已发生的业务操作失败；但关键字段缺失要在入口收敛为默认值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventService {

    private static final String ACTOR_SYSTEM = "SYSTEM";
    private static final String DEFAULT_APP = "sso";
    private static final String CATEGORY_ACCESS = "ACCESS";
    private static final String CATEGORY_LOGIN = "LOGIN";

    private final AuditEventMapper mapper;
    private final AuditSettingService settings;

    /** 批量写入，逐条转换，单条异常不影响同批其他记录。 */
    public void recordBatch(List<AuditEventRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        int failed = 0;
        int skipped = 0;
        for (AuditEventRequest req : requests) {
            // 接收端按全局设置收敛：登录/敏感事件强制落库，其余按采集开关与忽略规则过滤
            if (shouldDiscard(req)) {
                skipped++;
                continue;
            }
            try {
                mapper.insert(toEntity(req));
            } catch (Exception e) {
                failed++;
                log.warn("写入审计事件失败（已忽略）: action={} operator={} reason={}",
                        req.getAction(), req.getOperatorName(), e.getMessage());
            }
        }
        // 批次级汇总（不逐条刷屏）：网关按批异步上报，频率可控
        if (failed > 0) {
            log.warn("审计事件批量写入存在失败：total={} success={} failed={} skipped={}",
                    requests.size(), requests.size() - failed - skipped, failed, skipped);
        } else {
            log.info("审计事件批量写入成功：total={} skipped={}", requests.size(), skipped);
        }
    }

    /**
     * 接收端落库过滤。登录与敏感事件是合规必留项，永不因设置丢弃；
     * 其余按「采集总开关 → 忽略应用 → 忽略类别 → 忽略路径前缀」顺序判定。
     */
    private boolean shouldDiscard(AuditEventRequest req) {
        if (req == null) {
            return true;
        }
        String category = blankToDefault(req.getEventCategory(), CATEGORY_ACCESS).toUpperCase();
        // 合规必留：登录事件与敏感操作
        if (AuditSettingService.CATEGORY_LOGIN.equals(category)
                || AuditSettingService.CATEGORY_SENSITIVE.equals(category)) {
            return false;
        }
        // 采集总开关（源头关闭的接收端兜底）：ACCESS 由网关总开关控制，BUSINESS 由业务操作开关控制
        if (CATEGORY_ACCESS.equals(category) && !settings.bool(AuditSettingService.COLLECT_GATEWAY_ACCESS, true)) {
            return true;
        }
        if ("BUSINESS".equals(category) && !settings.bool(AuditSettingService.COLLECT_BUSINESS_OP, true)) {
            return true;
        }
        // 忽略应用
        String app = blankToDefault(req.getApp(), DEFAULT_APP);
        if (settings.ignoredApps().contains(app.toLowerCase())) {
            return true;
        }
        // 忽略类别
        if (settings.ignoredCategories().contains(category)) {
            return true;
        }
        // 忽略路径前缀
        String path = req.getRequestPath() == null ? "" : req.getRequestPath();
        for (String prefix : settings.ignoredPathPrefixes()) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public void record(AuditEventRequest req) {
        recordBatch(List.of(req));
    }

    private AuditEvent toEntity(AuditEventRequest req) {
        AuditEvent e = new AuditEvent();
        e.setId(IdUtils.nextId());
        e.setApp(blankToDefault(req.getApp(), DEFAULT_APP));
        e.setEventCategory(blankToDefault(req.getEventCategory(), CATEGORY_ACCESS));
        e.setAction(trim(req.getAction(), 128));
        e.setHttpMethod(trim(req.getHttpMethod(), 16));
        e.setRequestPath(trim(req.getRequestPath(), 512));
        String actorType = blankToDefault(req.getOperatorType(), ACTOR_SYSTEM);
        e.setOperatorType(actorType);
        e.setOperatorId(trim(req.getOperatorId(), 64));
        // 操作人：有值原样记（含尝试登录的用户名）；无主体的内部调用统一 SYSTEM，绝不因 id 为空丢弃
        String operator = req.getOperatorName() == null || req.getOperatorName().isBlank()
                ? ACTOR_SYSTEM : req.getOperatorName().trim();
        e.setOperatorName(trim(operator, 64));
        e.setTargetType(trim(req.getTargetType(), 64));
        e.setTargetId(trim(req.getTargetId(), 64));
        int fieldLimit = settings.integer(AuditSettingService.COLLECT_TRUNCATE_LENGTH, 1800);
        e.setDetail(mask(trim(req.getDetail(), 2000)));
        e.setRequestParams(mask(trim(req.getRequestParams(), fieldLimit)));
        e.setHttpStatus(req.getHttpStatus());
        e.setBizCode(req.getBizCode());
        e.setResponseSummary(mask(trim(req.getResponseSummary(), 2000)));
        e.setCostMs(req.getCostMs());
        // 隐私开关：关闭记录后不落库对应字段（登录事件保留 IP/UA 以便异地登录识别，不受关闭影响）
        boolean isLogin = CATEGORY_LOGIN.equals(e.getEventCategory());
        boolean recordIp = isLogin || settings.bool(AuditSettingService.PRIVACY_RECORD_IP, true);
        boolean recordUa = isLogin || settings.bool(AuditSettingService.PRIVACY_RECORD_UA, true);
        boolean recordOpId = settings.bool(AuditSettingService.PRIVACY_RECORD_OPERATOR_ID, true);
        e.setClientIp(recordIp ? trim(req.getClientIp(), 64) : "");
        e.setUserAgent(recordUa ? trim(req.getUserAgent(), 512) : "");
        if (!recordOpId) {
            e.setOperatorId("");
        }
        e.setLoginType(trim(req.getLoginType(), 32));
        e.setResult(trim(req.getResult(), 16));
        e.setFailReason(trim(req.getFailReason(), 500));
        Long occurredAt = req.getOccurredAt();
        e.setCreatedAt(occurredAt == null ? java.time.LocalDateTime.now()
                : java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(occurredAt),
                    java.time.ZoneId.systemDefault()));
        return e;
    }

    /** 审计事件分页查询（管理端「审计事件」页）。 */
    public PageResult<AuditEvent> page(int page, int size, String app, String eventCategory,
                                       String action, String operator, String httpMethod,
                                       String result, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<AuditEvent> w = new LambdaQueryWrapper<>();
        if (notBlank(app)) w.eq(AuditEvent::getApp, app.trim());
        if (notBlank(eventCategory)) w.eq(AuditEvent::getEventCategory, eventCategory.trim());
        if (notBlank(action)) w.like(AuditEvent::getAction, action.trim());
        if (notBlank(operator)) w.like(AuditEvent::getOperatorName, operator.trim());
        if (notBlank(httpMethod)) w.eq(AuditEvent::getHttpMethod, httpMethod.trim().toUpperCase());
        if (notBlank(result)) w.eq(AuditEvent::getResult, result.trim().toLowerCase());
        if (startDate != null) w.ge(AuditEvent::getCreatedAt, startDate.atStartOfDay());
        if (endDate != null) w.lt(AuditEvent::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        w.orderByDesc(AuditEvent::getCreatedAt).orderByDesc(AuditEvent::getId);
        IPage<AuditEvent> p = mapper.selectPage(new Page<>(page, size), w);
        return PageResult.of(p.getRecords(), p.getTotal(), page, size);
    }

    /** 登录审计分页：仍按原 sso_login_log 的字段形状返回，前端不用改列。 */
    public PageResult<LoginEventVO> loginPage(int page, int size, String keyword, String loginType) {
        LambdaQueryWrapper<AuditEvent> w = new LambdaQueryWrapper<>();
        w.eq(AuditEvent::getEventCategory, CATEGORY_LOGIN);
        if (notBlank(keyword)) w.like(AuditEvent::getOperatorName, keyword.trim());
        if (notBlank(loginType)) w.eq(AuditEvent::getLoginType, loginType.trim());
        w.orderByDesc(AuditEvent::getCreatedAt).orderByDesc(AuditEvent::getId);
        IPage<AuditEvent> p = mapper.selectPage(new Page<>(page, size), w);
        List<LoginEventVO> rows = new ArrayList<>();
        for (AuditEvent e : p.getRecords()) {
            LoginEventVO vo = new LoginEventVO();
            vo.setId(e.getId());
            vo.setAccountId(blankToNull(e.getOperatorId()));
            vo.setUsername(e.getOperatorName());
            vo.setLoginType(nullToEmpty(e.getLoginType()));
            vo.setResult(blankToDefault(e.getResult(), "success"));
            vo.setFailReason(blankToNull(e.getFailReason()));
            vo.setClientIp(e.getClientIp());
            vo.setUserAgent(e.getUserAgent());
            vo.setCreatedAt(e.getCreatedAt());
            rows.add(vo);
        }
        return PageResult.of(rows, p.getTotal(), page, size);
    }

    /** 审计概览：今日/本周/累计数量、登录失败数、按应用与类别分布、最近 7 天趋势。 */
    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("today", mapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
                .ge(AuditEvent::getCreatedAt, today.atStartOfDay())));
        out.put("week", mapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
                .ge(AuditEvent::getCreatedAt, today.minusDays(6).atStartOfDay())));
        out.put("total", mapper.selectCount(new LambdaQueryWrapper<>()));
        out.put("loginFail", mapper.selectCount(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getEventCategory, CATEGORY_LOGIN)
                .eq(AuditEvent::getResult, "fail")));

        List<Map<String, Object>> trend = mapper.selectMaps(
                new QueryWrapper<AuditEvent>()
                        .select("DATE(created_at) AS day, COUNT(*) AS cnt")
                        .ge("created_at", today.minusDays(6).atStartOfDay())
                        .groupBy("DATE(created_at)")
                        .orderByAsc("DATE(created_at)"));
        out.put("trend", trend);

        List<Map<String, Object>> byApp = mapper.selectMaps(
                new QueryWrapper<AuditEvent>()
                        .select("app, COUNT(*) AS cnt")
                        .ge("created_at", today.minusDays(29).atStartOfDay())
                        .groupBy("app").orderByDesc("cnt"));
        out.put("byApp", byApp);

        List<Map<String, Object>> byCategory = mapper.selectMaps(
                new QueryWrapper<AuditEvent>()
                        .select("event_category AS category, COUNT(*) AS cnt")
                        .ge("created_at", today.minusDays(29).atStartOfDay())
                        .groupBy("event_category").orderByDesc("cnt"));
        out.put("byCategory", byCategory);
        return out;
    }

    /** 账号中心概览的登录统计：口径与原 sso_login_log 聚合 SQL 一致。 */
    public LoginStatsResult loginStats(LoginStatsQuery query) {
        LoginStatsResult out = new LoginStatsResult();
        int days = query.getDays() <= 0 ? 14 : query.getDays();
        LocalDate today = LocalDate.now();

        out.setTodayCount(mapper.selectCount(loginScope(new QueryWrapper<>(), query)
                .ge("created_at", today.atStartOfDay())));

        List<Map<String, Object>> trendRows = mapper.selectMaps(
                loginScope(new QueryWrapper<>(), query)
                        .select("DATE(created_at) AS day, "
                                + "SUM(CASE WHEN result = 'success' THEN 1 ELSE 0 END) AS success, "
                                + "SUM(CASE WHEN result = 'fail' THEN 1 ELSE 0 END) AS fail")
                        .ge("created_at", today.minusDays(days - 1L).atStartOfDay())
                        .groupBy("DATE(created_at)")
                        .orderByAsc("DATE(created_at)"));
        List<LoginStatsResult.DayStat> trend = new ArrayList<>();
        for (Map<String, Object> row : trendRows) {
            LoginStatsResult.DayStat d = new LoginStatsResult.DayStat();
            d.setDay(String.valueOf(row.get("day")));
            d.setSuccess(number(row.get("success")));
            d.setFail(number(row.get("fail")));
            trend.add(d);
        }
        out.setTrend(trend);

        List<Map<String, Object>> typeRows = mapper.selectMaps(
                loginScope(new QueryWrapper<>(), query)
                        .select("login_type AS `key`, COUNT(*) AS cnt")
                        .ge("created_at", today.minusDays(29).atStartOfDay())
                        .groupBy("login_type").orderByDesc("cnt"));
        List<LoginStatsResult.TypeStat> types = new ArrayList<>();
        for (Map<String, Object> row : typeRows) {
            LoginStatsResult.TypeStat t = new LoginStatsResult.TypeStat();
            t.setKey(String.valueOf(row.get("key")));
            t.setCnt(number(row.get("cnt")));
            types.add(t);
        }
        out.setTypes(types);
        return out;
    }

    /** LOGIN 事件 + 数据范围条件；账号 ID 与用户名是「或」关系，覆盖失败日志只有用户名的场景。 */
    private QueryWrapper<AuditEvent> loginScope(QueryWrapper<AuditEvent> w, LoginStatsQuery query) {
        w.eq("event_category", CATEGORY_LOGIN);
        List<Long> accountIds = query.getAccountIds() == null ? List.of() : query.getAccountIds();
        List<String> usernames = query.getUsernames() == null ? List.of() : query.getUsernames();
        if (!accountIds.isEmpty() || !usernames.isEmpty()) {
            w.and(scope -> {
                if (!accountIds.isEmpty()) {
                    scope.in("operator_id", accountIds.stream().map(String::valueOf).toList());
                }
                if (!usernames.isEmpty()) {
                    if (!accountIds.isEmpty()) scope.or();
                    scope.in("operator_name", usernames);
                }
            });
        }
        return w;
    }

    /**
     * 二次脱敏：复用 common 静态打码（JSON/query/Bearer），再按设置里的敏感关键词打码。
     * 上游已脱敏一次，此处兜底；只做打码不再截断（长度已在 toEntity 按设置截断）。
     */
    private String mask(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String out = com.laimengchao.sso.utils.AuditDataMasker.mask(s);
        for (String kw : settings.sensitiveKeywords()) {
            if (kw.length() < 3) {
                continue;
            }
            // key=value / key: value / "key":"value" 三种形态打码，大小写不敏感
            out = out.replaceAll("(?i)(\"" + java.util.regex.Pattern.quote(kw) + "\"\\s*:\\s*)\"[^\"]*\"", "$1\"***\"");
            out = out.replaceAll("(?i)(" + java.util.regex.Pattern.quote(kw) + "\\s*[:=]\\s*)[^&,\\s\"]+", "$1***");
        }
        return out;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String blankToDefault(String s, String def) {
        return notBlank(s) ? s.trim() : def;
    }

    private String blankToNull(String s) {
        return notBlank(s) ? s.trim() : null;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String trim(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
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
