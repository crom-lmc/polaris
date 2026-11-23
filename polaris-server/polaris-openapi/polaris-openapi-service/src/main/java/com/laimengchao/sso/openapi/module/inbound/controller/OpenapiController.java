package com.laimengchao.sso.openapi.module.inbound.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.openapi.module.app.service.ClientTokenService;
import com.laimengchao.sso.openapi.module.inbound.service.InboundService;
import com.laimengchao.sso.openapi.security.OpenapiAuthFilter;
import com.laimengchao.sso.openapi.security.dto.CallerIdentity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 开放平台对外接口（首批四类只读 + 换令牌）。
 * <p>
 * 鉴权由 {@link OpenapiAuthFilter} 统一完成，这里只做参数绑定与转发编排。
 */
@RestController
@RequestMapping("/api/open")
@RequiredArgsConstructor
public class OpenapiController {

    private final InboundService inboundService;
    private final ClientTokenService clientTokenService;

    /** 换取第三方机器令牌（client_credentials）：本地验凭证 + sso-auth RS256 签发。 */
    @PostMapping("/token")
    public R<Map<String, Object>> token(@RequestParam(value = "app_code", required = false) String appCode,
                                        @RequestParam(value = "app_secret", required = false) String appSecret) {
        ClientTokenService.TokenResult result = clientTokenService.issue(appCode, appSecret);
        return R.ok(Map.of(
                "access_token", result.accessToken(),
                "token_type", "Bearer",
                "expires_in", result.expiresIn()));
    }

    /* ---------------- 主数据 ---------------- */

    @GetMapping("/v1/master/categories")
    public R<?> masterCategories(HttpServletRequest req) {
        return invoke("inbound.master.categories", req, null, Map.of());
    }

    @GetMapping("/v1/master/{type}/items")
    public R<?> masterItems(@PathVariable String type, HttpServletRequest req) {
        return invoke("inbound.master.items", req, null, Map.of("type", type));
    }

    /* ---------------- 字典 ---------------- */

    @GetMapping("/v1/dict/{typeCode}/items")
    public R<?> dictItems(@PathVariable String typeCode, HttpServletRequest req) {
        return invoke("inbound.dict.items", req, null, Map.of("typeCode", typeCode));
    }

    @PostMapping("/v1/dict/batch")
    public R<?> dictBatch(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest req) {
        return invoke("inbound.dict.batch", req, body, Map.of());
    }

    /* ---------------- 人员 ---------------- */

    @GetMapping("/v1/employees/active")
    public R<?> employeesActive(HttpServletRequest req) {
        return invoke("inbound.employee.active", req, null, Map.of());
    }

    /* ---------------- 组织 ---------------- */

    @GetMapping("/v1/orgs/tree")
    public R<?> orgTree(HttpServletRequest req) {
        return invoke("inbound.org.tree", req, null, Map.of());
    }

    @GetMapping("/v1/orgs/{id}/employees")
    public R<?> orgEmployees(@PathVariable Long id, HttpServletRequest req) {
        return invoke("inbound.org.employees", req, null, Map.of("id", String.valueOf(id)));
    }

    /* ---------------- 内部工具 ---------------- */

    private R<?> invoke(String apiCode, HttpServletRequest req, Object body, Map<String, String> pathVars) {
        CallerIdentity identity = (CallerIdentity) req.getAttribute(OpenapiAuthFilter.ATTR_IDENTITY);
        String traceId = req.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        Map<String, Object> requestParams = requestParams(req, pathVars, body);
        Object data = inboundService.invoke(new InboundService.InvokeContext(
                apiCode, identity, pathVars, body,
                req.getMethod(), req.getRequestURI(), clientIp(req), traceId, requestParams));
        return R.ok(data);
    }

    /** 日志用入参：路径变量 + 查询参数 + JSON body（GET 无 body 时仍能看到参数）。 */
    private Map<String, Object> requestParams(HttpServletRequest req, Map<String, String> pathVars, Object body) {
        Map<String, Object> params = new HashMap<>();
        req.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length == 1) {
                params.put(key, values[0]);
            } else if (values != null && values.length > 1) {
                params.put(key, java.util.Arrays.asList(values));
            }
        });
        params.putAll(pathVars);
        if (body != null) {
            params.put("body", body);
        }
        return params;
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
