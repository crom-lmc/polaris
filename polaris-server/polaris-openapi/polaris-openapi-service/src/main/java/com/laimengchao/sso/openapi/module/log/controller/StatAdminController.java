package com.laimengchao.sso.openapi.module.log.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.openapi.module.log.service.StatService;
import com.laimengchao.sso.openapi.module.log.vo.LogQuery;
import com.laimengchao.sso.openapi.security.admin.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 统计报表管理接口（SSO 用户令牌 + @Permit）。概览页与统计报表页共用。 */
@RestController
@RequestMapping("/api/open/admin/stat")
@RequiredArgsConstructor
public class StatAdminController {

    private final StatService statService;

    @GetMapping("/overview")
    @Permit(PermissionCodes.OPENAPI_STAT_VIEW)
    public R<Map<String, Object>> overview(@RequestParam(required = false) String direction,
                                           @RequestParam(required = false) String appCode,
                                           @RequestParam(required = false) String apiCode,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        LogQuery q = new LogQuery();
        q.setDirection(direction);
        q.setAppCode(appCode);
        q.setApiCode(apiCode);
        q.setFrom(from);
        q.setTo(to);
        return R.ok(statService.overview(q));
    }

    /** 按天调用趋势（独立于概览顶部日期），支持自定义时间范围。 */
    @GetMapping("/trend")
    @Permit(PermissionCodes.OPENAPI_STAT_VIEW)
    public R<java.util.List<Map<String, Object>>> trend(@RequestParam(required = false) String from,
                                                        @RequestParam(required = false) String to) {
        return R.ok(statService.trend(from, to));
    }

    /** 失败 TOP API，支持自定义时间范围与条数。 */
    @GetMapping("/top-failed")
    @Permit(PermissionCodes.OPENAPI_STAT_VIEW)
    public R<java.util.List<Map<String, Object>>> topFailed(@RequestParam(required = false) String from,
                                                            @RequestParam(required = false) String to,
                                                            @RequestParam(defaultValue = "10") int limit) {
        return R.ok(statService.topFailed(from, to, limit));
    }

    /** 最近异常，支持自定义时间范围与条数。 */
    @GetMapping("/recent-errors")
    @Permit(PermissionCodes.OPENAPI_STAT_VIEW)
    public R<java.util.List<Map<String, Object>>> recentErrors(@RequestParam(required = false) String from,
                                                               @RequestParam(required = false) String to,
                                                               @RequestParam(defaultValue = "10") int limit) {
        return R.ok(statService.recentErrors(from, to, limit));
    }
}
