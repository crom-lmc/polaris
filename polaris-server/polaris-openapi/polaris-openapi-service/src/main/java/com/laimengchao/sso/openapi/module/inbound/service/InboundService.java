package com.laimengchao.sso.openapi.module.inbound.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.file.client.api.FileApi;
import com.laimengchao.sso.file.client.dto.FileUploadRequest;
import com.laimengchao.sso.file.client.dto.SignUrlRequest;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.endpoint.entity.ApiOpenEndpoint;
import com.laimengchao.sso.openapi.module.endpoint.mapper.ApiOpenEndpointMapper;
import com.laimengchao.sso.openapi.module.integration.EhrInternalApi;
import com.laimengchao.sso.openapi.module.integration.SettingsInternalApi;
import com.laimengchao.sso.openapi.module.log.entity.ApiCallLog;
import com.laimengchao.sso.openapi.module.log.service.CallLogService;
import com.laimengchao.sso.openapi.module.log.service.MaskUtils;
import com.laimengchao.sso.openapi.security.ApiPermissionChecker;
import com.laimengchao.sso.openapi.security.RateLimiter;
import com.laimengchao.sso.openapi.security.dto.CallerIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 接口进（Inbound）核心编排：目录定位 → 权限校验 → 限流 → 下游转发 → 日志落库。
 * <p>
 * 四类只读接口都由本类统一处理，controller 只负责参数绑定与鉴权入口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundService {

    private final ApiOpenEndpointMapper endpointMapper;
    private final SettingsInternalApi settingsApi;
    private final EhrInternalApi ehrApi;
    private final FileApi fileApi;
    private final RateLimiter rateLimiter;
    private final CallLogService callLogService;

    /** 执行一个开放 API 调用，返回下游 data。 */
    public Object invoke(InvokeContext ctx) {
        ApiOpenEndpoint ep = endpointMapper.selectByCode(ctx.apiCode());
        if (ep == null || ep.getStatus() == null || ep.getStatus() != 1) {
            log.warn("开放接口不存在或已停用：apiCode={} app={} traceId={}",
                    ctx.apiCode(), ctx.identity().getAppCode(), ctx.traceId());
            writeLog(ctx, null, 404, OpenapiErrorCode.API_NOT_FOUND.getCode(), requestJson(ctx), null,
                    OpenapiErrorCode.API_NOT_FOUND.getMessage());
            throw new BizException(OpenapiErrorCode.API_NOT_FOUND);
        }

        // 权限校验：令牌 perms 为已授权接口编码（一行一个接口）。
        // 兼容旧令牌仍是权限码的情况，两种任一命中即放行；停用单个接口后其 api_code 不再进入新令牌。
        // 未配置权限码的历史接口保持原有放行语义。
        String requiredPerm = ep.getRequiredPerm();
        if (requiredPerm != null && !requiredPerm.isBlank()
                && !ApiPermissionChecker.hasPermission(ep.getApiCode(), requiredPerm,
                        ctx.identity().getPermissions())) {
            log.warn("开放接口权限不足：apiCode={} app={} traceId={}",
                    ctx.apiCode(), ctx.identity().getAppCode(), ctx.traceId());
            writeLog(ctx, ep, 403, OpenapiErrorCode.PERM_DENIED.getCode(), requestJson(ctx), null, null);
            throw new BizException(OpenapiErrorCode.PERM_DENIED);
        }

        // 限流
        try {
            rateLimiter.check(ctx.identity().getAppCode(), ep.getApiCode(),
                    ep.getRateLimitPerMin() == null ? 60 : ep.getRateLimitPerMin());
        } catch (BizException e) {
            log.warn("开放接口触发限流：apiCode={} app={} traceId={}",
                    ctx.apiCode(), ctx.identity().getAppCode(), ctx.traceId());
            writeLog(ctx, ep, 429, OpenapiErrorCode.RATE_LIMITED.getCode(), requestJson(ctx), null, e.getMessage());
            throw e;
        }

        long start = System.currentTimeMillis();
        Object data;
        try {
            data = dispatch(ep, ctx.pathVars(), ctx.body());
            int costMs = (int) (System.currentTimeMillis() - start);
            writeLog(ctx, ep, 200, 0, requestJson(ctx), toJson(data), null, costMs);
            log.info("开放接口调用成功：apiCode={} app={} traceId={} costMs={}",
                    ctx.apiCode(), ctx.identity().getAppCode(), ctx.traceId(), costMs);
            return data;
        } catch (BizException e) {
            log.warn("开放接口业务失败：apiCode={} traceId={} bizCode={} reason={}",
                    ctx.apiCode(), ctx.traceId(), e.getCode(), e.getMessage());
            int http = e.getErrorCode() != null && e.getErrorCode().httpStatus() > 0
                    ? e.getErrorCode().httpStatus() : 500;
            writeLog(ctx, ep, http, e.getCode(),
                    requestJson(ctx), null, e.getMessage(),
                    (int) (System.currentTimeMillis() - start));
            throw e;
        } catch (Exception e) {
            log.error("开放接口下游调用失败：apiCode={} traceId={}", ep.getApiCode(), ctx.traceId(), e);
            writeLog(ctx, ep, 502, OpenapiErrorCode.CHANNEL_ERROR.getCode(),
                    requestJson(ctx), null, e.getMessage(),
                    (int) (System.currentTimeMillis() - start));
            throw new BizException(OpenapiErrorCode.CHANNEL_ERROR);
        }
    }

    /** 按 apiCode 分派到具体下游 Feign 调用。 */
    private Object dispatch(ApiOpenEndpoint ep, Map<String, String> vars, Object body) {
        return switch (ep.getApiCode()) {
            case "inbound.master.categories" -> unwrap(settingsApi.bizCategories());
            case "inbound.master.items" -> unwrap(settingsApi.bizItems(vars.get("type")));
            case "inbound.dict.items" -> unwrap(settingsApi.dictItems(vars.get("typeCode")));
            case "inbound.dict.batch" -> unwrap(settingsApi.dictBatch(asMap(body)));
            case "inbound.employee.active" -> unwrap(ehrApi.activeEmployees());
            case "inbound.org.tree" -> unwrap(ehrApi.orgTree());
            case "inbound.org.employees" -> unwrap(ehrApi.orgEmployees(Long.valueOf(vars.get("id"))));
            /* 文件服务对外接口：经 file-client 转发到 file-service /internal/files/** */
            case "inbound.file.upload" -> unwrapFile(fileApi.upload(toUploadRequest(asMap(body))));
            case "inbound.file.meta" -> unwrapFile(fileApi.meta(vars.get("id")));
            case "inbound.file.download" -> unwrapFile(fileApi.sign(vars.get("id"), toSignRequest(vars)));
            case "inbound.file.delete" -> unwrapFile(fileApi.delete(vars.get("id")));
            default -> throw new BizException(OpenapiErrorCode.API_NOT_FOUND);
        };
    }

    /** 文件服务返回的 R 是客户端 jar 内的独立类型，单独解包（避免依赖 common.R）。 */
    private Object unwrapFile(com.laimengchao.sso.file.client.dto.R<?> r) {
        if (r == null || !r.ok()) {
            throw new BizException(OpenapiErrorCode.CHANNEL_ERROR,
                    r == null ? "文件服务无响应" : "文件服务返回：" + r.getMessage());
        }
        return r.getData();
    }

    private FileUploadRequest toUploadRequest(Map<String, Object> body) {
        FileUploadRequest req = new FileUploadRequest();
        req.setBizType(str(body.get("bizType")));
        req.setVisibility(str(body.get("visibility")));
        req.setOriginalName(str(body.get("originalName")));
        req.setContentType(str(body.get("contentType")));
        req.setContent(str(body.get("content")));
        req.setOwnerType(str(body.get("ownerType")));
        req.setOwnerId(str(body.get("ownerId")));
        Object uploader = body.get("uploaderId");
        req.setUploaderId(uploader == null ? 0L : Long.valueOf(String.valueOf(uploader)));
        return req;
    }

    private SignUrlRequest toSignRequest(Map<String, String> vars) {
        SignUrlRequest req = new SignUrlRequest();
        req.setPurpose(vars.getOrDefault("purpose", "DOWNLOAD"));
        String expires = vars.get("expiresSeconds");
        req.setExpiresSeconds(expires == null || expires.isBlank() ? null : Long.valueOf(expires));
        return req;
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Object unwrap(R<?> r) {
        if (r == null || r.getCode() != 0) {
            throw new BizException(OpenapiErrorCode.CHANNEL_ERROR,
                    r == null ? "下游无响应" : "下游返回：" + r.getMessage());
        }
        return r.getData();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object body) {
        return body instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MaskUtils.mask(com.laimengchao.sso.utils.JsonUtils.toJson(obj));
        } catch (Exception e) {
            return "[serialize-error]";
        }
    }

    /** 请求入参：GET 没有 body，用路径变量 + 查询参数（POST 再带上 body）统一记录。 */
    private String requestJson(InvokeContext ctx) {
        Map<String, Object> params = ctx.requestParams();
        Object source = params == null || params.isEmpty() ? ctx.body() : params;
        // 文件上传的 base64 content 绝不落库：替换为摘要占位，只保留文件名/类型/大小便于排查。
        return toJson(sanitizeFileBody(source));
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeFileBody(Object source) {
        if (!(source instanceof Map<?, ?> raw)) {
            return source;
        }
        Map<String, Object> copy = new java.util.HashMap<>((Map<String, Object>) raw);
        Object bodyVal = copy.get("body");
        if (bodyVal instanceof Map<?, ?> rawBody) {
            Map<String, Object> body = new java.util.HashMap<>((Map<String, Object>) rawBody);
            if (body.containsKey("content")) {
                Object content = body.get("content");
                int len = content == null ? 0 : String.valueOf(content).length();
                body.put("content", "[base64 omitted, " + len + " chars]");
            }
            copy.put("body", body);
        }
        return copy;
    }

    private void writeLog(InvokeContext ctx, ApiOpenEndpoint ep, int httpStatus, int bizCode,
                          String req, String resp, String error) {
        writeLog(ctx, ep, httpStatus, bizCode, req, resp, error, 0);
    }

    private void writeLog(InvokeContext ctx, ApiOpenEndpoint ep, int httpStatus, int bizCode,
                          String req, String resp, String error, int costMs) {
        ApiCallLog entry = new ApiCallLog();
        entry.setTraceId(ctx.traceId());
        entry.setDirection("INBOUND");
        entry.setAppCode(ctx.identity() == null ? "" : ctx.identity().getAppCode());
        entry.setApiCode(ctx.apiCode());
        entry.setHttpMethod(ctx.method());
        entry.setRequestPath(ctx.requestPath());
        entry.setReqMasked(req);
        entry.setRespMasked(resp);
        entry.setHttpStatus(httpStatus);
        entry.setBizCode(String.valueOf(bizCode));
        entry.setCostMs(costMs);
        entry.setClientIp(ctx.clientIp());
        entry.setErrorMsg(error);
        entry.setCreatedAt(LocalDateTime.now());
        callLogService.save(entry);
    }

    /** 调用上下文。 */
    public record InvokeContext(String apiCode, CallerIdentity identity, Map<String, String> pathVars,
                                Object body, String method, String requestPath, String clientIp,
                                String traceId, Map<String, Object> requestParams) {
    }
}
