package com.laimengchao.sso.audit.client.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.audit.client.annotation.AuditLog;
import com.laimengchao.sso.audit.client.api.AuditApi;
import com.laimengchao.sso.audit.client.dto.AuditEventRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 业务方法审计切面（自动留痕，新接口无需标注即可覆盖）。
 * <p>
 * 拦截范围：{@code @RestController}/{@code @Controller} 类里的写方法（POST/PUT/PATCH/DELETE），
 * 以及带 {@link AuditLog} 注解的方法。GET 等只读由网关 ACCESS 兜底，这里不重复记。
 * <p>
 * 记录内容：脱敏入参、业务返回码 R.code、响应摘要、耗时、结果成功/失败、操作人（网关注入的可信头）。
 * 上报走 audit-client 的 {@link AuditApi}，异步、失败只告警——审计绝不阻断业务。
 */
@Aspect
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private static final String HEADER_ACCOUNT_ID = "X-Account-Id";
    private static final String HEADER_USERNAME = "X-Account-Name";
    private static final String HEADER_APP = "X-App-Code";

    private static final int MAX_PARAM_LENGTH = 1800;

    private final AuditApi auditApi;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public AuditLogAspect(AuditApi auditApi, boolean enabled) {
        this.auditApi = auditApi;
        this.enabled = enabled;
        this.objectMapper = new ObjectMapper();
    }

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) "
            + "|| within(@org.springframework.stereotype.Controller *)")
    public void controllers() {
    }

    @Pointcut("@annotation(com.laimengchao.sso.audit.client.annotation.AuditLog) "
            + "|| @within(com.laimengchao.sso.audit.client.annotation.AuditLog)")
    public void annotated() {
    }

    @Around("controllers() || annotated()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        if (!enabled || !isWriteOrAnnotated(pjp)) {
            return pjp.proceed();
        }
        long start = System.nanoTime();
        Throwable failure = null;
        Object result = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            try {
                report(pjp, result, failure, (System.nanoTime() - start) / 1_000_000L);
            } catch (Exception e) {
                log.warn("业务审计上报失败（已忽略）: {}", e.getMessage());
            }
        }
    }

    /** 写方法（POST/PUT/PATCH/DELETE）或有 @AuditLog 注解才记；GET 由网关兜底。 */
    private boolean isWriteOrAnnotated(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        AuditLog ann = AnnotatedElementUtils.findMergedAnnotation(method, AuditLog.class);
        if (ann != null && ann.ignore()) {
            return false;
        }
        if (ann != null) {
            return true;
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return false;
        }
        String m = request.getMethod();
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m) || "DELETE".equals(m);
    }

    private void report(ProceedingJoinPoint pjp, Object result, Throwable failure, long costMs) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        AuditLog ann = AnnotatedElementUtils.findMergedAnnotation(method, AuditLog.class);
        if (ann == null) {
            ann = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), AuditLog.class);
        }

        HttpServletRequest request = currentRequest();
        String accountId = request == null ? "" : nvl(request.getHeader(HEADER_ACCOUNT_ID));
        String username = request == null ? "" : nvl(request.getHeader(HEADER_USERNAME));
        String app = request == null ? "" : nvl(request.getHeader(HEADER_APP));
        boolean system = accountId.isEmpty() && username.isEmpty();
        String operatorType = system ? "SYSTEM" : "USER";
        String operatorName = system ? "SYSTEM" : username;

        String action = ann != null && !ann.action().isEmpty()
                ? ann.action()
                : method.getDeclaringClass().getSimpleName() + "." + method.getName();
        String targetType = ann == null ? "" : ann.targetType();

        // 入参：跳过 servlet/multipart 等不可序列化对象，其余转 JSON 后脱敏
        String params = maskParams(sig.getParameterNames(), pjp.getArgs());
        // 出参：从 R 结构与异常提取 code/msg
        Integer bizCode = null;
        String responseSummary;
        boolean success = failure == null;
        if (failure != null) {
            responseSummary = "EXCEPTION " + failure.getClass().getSimpleName();
        } else {
            Map<String, Object> r = toResultMap(result);
            if (r != null) {
                Object code = r.get("code");
                bizCode = code instanceof Number n ? n.intValue() : parseInt(code);
                Object msg = r.get("message");
                success = bizCode == null || bizCode == 0;
                responseSummary = "code=" + (bizCode == null ? "?" : bizCode)
                        + (msg == null ? "" : ";msg=" + nvl(String.valueOf(msg)));
            } else {
                responseSummary = "code=?;msg=<non-R result>";
            }
        }
        responseSummary = mask(responseSummary);

        AuditEventRequest req = new AuditEventRequest();
        req.setApp(app.isEmpty() ? "sso" : app);
        req.setEventCategory(success ? "BUSINESS" : "SENSITIVE");
        req.setAction(action);
        req.setHttpMethod(request == null ? "" : request.getMethod());
        req.setRequestPath(request == null ? "" : request.getRequestURI());
        req.setOperatorType(operatorType);
        req.setOperatorId(accountId);
        req.setOperatorName(operatorName);
        req.setTargetType(targetType);
        req.setTargetId("");
        req.setDetail("result=" + (success ? "success" : "fail"));
        req.setRequestParams(params);
        req.setBizCode(bizCode);
        req.setResponseSummary(responseSummary);
        req.setCostMs(costMs);
        req.setResult(success ? "success" : "fail");
        req.setClientIp(request == null ? "" : clientIp(request));
        req.setUserAgent(request == null ? "" : nvl(request.getHeader("User-Agent")));
        req.setOccurredAt(System.currentTimeMillis());

        final AuditEventRequest payload = req;
        CompletableFuture.runAsync(() -> {
            try {
                auditApi.report(payload);
            } catch (Exception e) {
                log.warn("审计上报失败（已忽略）: action={} reason={}", action, e.getMessage());
            }
        });
    }

    private Map<String, Object> toResultMap(Object result) {
        if (result == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(result);
            Object node = objectMapper.readValue(json, Object.class);
            if (node instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                map.forEach((k, v) -> out.put(String.valueOf(k), v));
                return out;
            }
        } catch (Exception ignore) {
            // 非 JSON 可序列化结果，按未知处理
        }
        return null;
    }

    private String maskParams(String[] names, Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> skipTypes = List.of(
                "ServletRequest", "ServletResponse", "HttpServletRequest", "HttpServletResponse",
                "MultipartFile", "BindingResult", "Principal");
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            String type = arg.getClass().getSimpleName();
            if (arg instanceof MultipartFile || arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse
                    || skipTypes.stream().anyMatch(t -> type.contains(t))) {
                continue;
            }
            String name = i < names.length ? names[i] : "arg" + i;
            params.put(name, arg);
        }
        if (params.isEmpty()) {
            return "";
        }
        try {
            return mask(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            return "";
        }
    }

    /** 敏感键打码 + 截断，与 common 的 AuditDataMasker 规则保持一致（audit-client 不依赖 common）。 */
    private String mask(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = raw.replaceAll("(?i)(\"(?:password|passwd|pwd|token|accessToken|refreshToken|secret|authorization|apiKey|idCard|idcard|mobile|phone|bankAccount)\"\\s*:\\s*)\"[^\"]*\"", "$1\"***\"");
        s = s.replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/-]+=*", "$1***");
        return s.length() <= MAX_PARAM_LENGTH ? s : s.substring(0, MAX_PARAM_LENGTH) + "...(truncated)";
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return nvl(request.getRemoteAddr());
    }

    private Integer parseInt(Object code) {
        if (code == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(code));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
