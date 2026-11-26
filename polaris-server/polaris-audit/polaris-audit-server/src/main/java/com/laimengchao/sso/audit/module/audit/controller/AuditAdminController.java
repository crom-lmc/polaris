package com.laimengchao.sso.audit.module.audit.controller;

import com.laimengchao.sso.audit.module.audit.service.impl.AuditSettingServiceImpl;
import com.laimengchao.sso.audit.module.audit.entity.AuditEvent;
import com.laimengchao.sso.audit.module.audit.entity.SystemLog;
import com.laimengchao.sso.audit.module.audit.service.AuditEventService;
import com.laimengchao.sso.audit.client.dto.AuditEventRequest;
import com.laimengchao.sso.audit.module.audit.service.AuditSettingService;
import com.laimengchao.sso.audit.module.audit.service.SystemLogService;
import com.laimengchao.sso.audit.module.audit.vo.AuditSettingsSaveRequest;
import com.laimengchao.sso.audit.module.audit.vo.LoginEventVO;
import com.laimengchao.sso.audit.security.Permit;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.utils.AccountContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final AuditSettingService auditSettingService;

    /** 审计全局设置：采集/忽略/留存/脱敏/导出策略，返回含默认值的完整 K-V。 */
    @GetMapping("/settings")
    @Permit(value = PermissionCodes.AUDIT_SETTING_VIEW,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<Map<String, String>> settings() {
        return R.ok(auditSettingService.all());
    }

    /** 批量保存审计全局设置；仅管理员可写。 */
    @PutMapping("/settings")
    @Permit(value = PermissionCodes.AUDIT_SETTING_EDIT,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<Void> saveSettings(@RequestBody AuditSettingsSaveRequest req) {
        auditSettingService.save(req == null ? null : req.getSettings());
        return R.ok();
    }

    /** 恢复全部设置为内置默认值。 */
    @PostMapping("/settings/reset")
    @Permit(value = PermissionCodes.AUDIT_SETTING_EDIT,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public R<Void> resetSettings() {
        auditSettingService.resetAll();
        return R.ok();
    }

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

    /**
     * 审计事件导出 CSV：筛选条件与列表一致，受全局设置 export.enabled / export.maxRows 约束；
     * 水印文案按 export.watermarkText 烧录到首行注释，{username}/{date} 用操作人与当天替换。
     * 每次导出写一条 SENSITIVE 审计事件留痕（合规必留，不受其他开关影响）。
     */
    @GetMapping("/events/export")
    @Permit(value = PermissionCodes.AUDIT_EVENTS_EXPORT,
            app = {PermissionCodes.APP_AUDIT, PermissionCodes.APP_SSO})
    public void exportEvents(@RequestParam(required = false) String app,
                             @RequestParam(required = false) String eventCategory,
                             @RequestParam(required = false) String action,
                             @RequestParam(required = false) String operator,
                             @RequestParam(required = false) String httpMethod,
                             @RequestParam(required = false) String result,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                             @RequestParam(required = false) String purpose,
                             HttpServletResponse response) throws IOException {
        if (!auditSettingService.bool(AuditSettingServiceImpl.EXPORT_ENABLED, false)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("审计事件导出未开放，请联系管理员在全局设置中开启");
            return;
        }
        List<AuditEvent> rows = auditEventService.export(app, eventCategory, action, operator,
                httpMethod, result, startDate, endDate);

        String operatorName = nvl(AccountContext.getUsername());
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String watermark = nvl(auditSettingService.get(AuditSettingServiceImpl.EXPORT_WATERMARK));
        watermark = watermark.replace("{username}", operatorName).replace("{date}", today);

        response.setContentType("text/csv;charset=UTF-8");
        String fileName = URLEncoder.encode("audit-events-" + today + ".csv", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // UTF-8 BOM，Excel 直接打开不乱码
        if (!watermark.isBlank()) {
            sb.append('#').append(csv(watermark)).append('\n');
        }
        sb.append("id,时间,应用,类别,动作,方法,路径,操作人,操作人ID,目标类型,目标ID,")
                .append("详情,请求参数,HTTP状态,业务码,响应摘要,耗时ms,结果,客户端IP,User-Agent\n");
        for (AuditEvent e : rows) {
            sb.append(csv(e.getId())).append(',')
                    .append(csv(fmt(e.getCreatedAt()))).append(',')
                    .append(csv(e.getApp())).append(',')
                    .append(csv(e.getEventCategory())).append(',')
                    .append(csv(e.getAction())).append(',')
                    .append(csv(e.getHttpMethod())).append(',')
                    .append(csv(e.getRequestPath())).append(',')
                    .append(csv(e.getOperatorName())).append(',')
                    .append(csv(e.getOperatorId())).append(',')
                    .append(csv(e.getTargetType())).append(',')
                    .append(csv(e.getTargetId())).append(',')
                    .append(csv(e.getDetail())).append(',')
                    .append(csv(e.getRequestParams())).append(',')
                    .append(csv(e.getHttpStatus())).append(',')
                    .append(csv(e.getBizCode())).append(',')
                    .append(csv(e.getResponseSummary())).append(',')
                    .append(csv(e.getCostMs())).append(',')
                    .append(csv(e.getResult())).append(',')
                    .append(csv(e.getClientIp())).append(',')
                    .append(csv(e.getUserAgent())).append('\n');
        }
        response.getWriter().write(sb.toString());

        recordExport(rows.size(), app, eventCategory, startDate, endDate, operatorName, purpose);
    }

    /** 导出行为留痕：SENSITIVE 事件合规必留，失败只告警不影响已生成的导出文件。 */
    private void recordExport(int rows, String app, String category, LocalDate from, LocalDate to,
                              String operatorName, String purpose) {
        try {
            AuditEventRequest req = new AuditEventRequest();
            req.setApp("audit");
            req.setEventCategory("SENSITIVE");
            req.setAction("EXPORT_AUDIT_EVENTS");
            req.setHttpMethod("GET");
            req.setRequestPath("/api/audit/admin/events/export");
            req.setOperatorType("USER");
            req.setOperatorName(operatorName.isBlank() ? "SYSTEM" : operatorName);
            req.setTargetType("AUDIT_EVENT");
            req.setTargetId("rows=" + rows);
            req.setDetail("导出审计事件 rows=" + rows
                    + ";app=" + nvl(app) + ";category=" + nvl(category)
                    + ";from=" + (from == null ? "-" : from)
                    + ";to=" + (to == null ? "-" : to)
                    + ";purpose=" + (purpose == null || purpose.isBlank() ? "未填写" : purpose));
            req.setResult("success");
            auditEventService.record(req);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditAdminController.class)
                    .warn("记录审计导出行为失败（已忽略）: {}", e.getMessage());
        }
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    private String csv(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o).replace("\"", "\"\"").replace("\r", " ").replace("\n", " ");
        return "\"" + s + "\"";
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
