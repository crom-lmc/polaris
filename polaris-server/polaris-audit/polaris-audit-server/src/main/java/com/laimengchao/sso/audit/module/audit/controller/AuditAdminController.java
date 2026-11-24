package com.laimengchao.sso.audit.module.audit.controller;

import com.laimengchao.sso.audit.module.audit.entity.AuditEvent;
import com.laimengchao.sso.audit.module.audit.entity.SystemLog;
import com.laimengchao.sso.audit.module.audit.service.AuditEventService;
import com.laimengchao.sso.audit.module.audit.service.SystemLogService;
import com.laimengchao.sso.audit.module.audit.vo.LoginEventVO;
import com.laimengchao.sso.audit.security.Permit;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 审计中心管理端：由网关验签注入身份头，@Permit 校验 audit:* 权限码。
 * <p>
 * 审计前端目前走统一登录中心直连登录（令牌 app_code=sso），因此这些接口同时放行 sso 与 audit 两个应用域。
 */
@RestController
@RequestMapping("/api/audit/admin")
@RequiredArgsConstructor
public class AuditAdminController {

    private final AuditEventService auditEventService;
    private final SystemLogService systemLogService;

    @GetMapping("/overview")
    @Permit(value = PermissionCodes.AUDIT_OVERVIEW_VIEW,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<Map<String, Object>> overview() {
        return R.ok(auditEventService.overview());
    }

    /** 统一事件流：按应用/类别/动作/操作人或结果筛选，EHR 敏感操作用 eventCategory=SENSITIVE 与 app=ehr。 */
    @GetMapping("/events")
    @Permit(value = PermissionCodes.AUDIT_EVENTS_LIST,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<PageResult<AuditEvent>> events(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String eventCategory,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(auditEventService.page(page, size, app, eventCategory, action, operator,
                httpMethod, result, startDate, endDate));
    }

    /** 登录审计：字段形状与原登录日志一致，含尝试登录用户名与失败原因。 */
    @GetMapping("/login-events")
    @Permit(value = PermissionCodes.AUDIT_LOGIN_LIST,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<PageResult<LoginEventVO>> loginEvents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String loginType) {
        return R.ok(auditEventService.loginPage(page, size, keyword, loginType));
    }

    /** 系统运行日志：各服务打的 log，按应用/级别/关键字/日期筛选。 */
    @GetMapping("/system-logs")
    @Permit(value = PermissionCodes.AUDIT_SYSTEM_LOG_LIST,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<PageResult<SystemLog>> systemLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(systemLogService.page(page, size, app, level, keyword, startDate, endDate));
    }

    /** 系统日志筛选项：最近 30 天有日志上报的应用列表。 */
    @GetMapping("/system-logs/apps")
    @Permit(value = PermissionCodes.AUDIT_SYSTEM_LOG_LIST,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<List<String>> systemLogApps() {
        return R.ok(systemLogService.apps());
    }

    /** 系统日志概览：今日/累计/ERROR/WARN 数与应用、级别分布。 */
    @GetMapping("/system-logs/overview")
    @Permit(value = PermissionCodes.AUDIT_SYSTEM_LOG_LIST,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<Map<String, Object>> systemLogOverview() {
        return R.ok(systemLogService.overview());
    }

    /** 日志文件列表：按「应用 + 日期」分组，供前端列出"某应用某天的日志"。 */
    @GetMapping("/system-logs/groups")
    @Permit(value = PermissionCodes.AUDIT_SYSTEM_LOG_LIST,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<List<Map<String, Object>>> systemLogGroups(
            @RequestParam(required = false) String app,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(systemLogService.logFiles(app, startDate, endDate));
    }

    /** 终端日志流：按应用 + 日期拉当天日志（时间正序），供大抽屉以终端风格展示。 */
    @GetMapping("/system-logs/stream")
    @Permit(value = PermissionCodes.AUDIT_SYSTEM_LOG_LIST,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<List<SystemLog>> systemLogStream(
            @RequestParam String app,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1000") int limit) {
        return R.ok(systemLogService.stream(app, day, level, keyword, limit));
    }
}
