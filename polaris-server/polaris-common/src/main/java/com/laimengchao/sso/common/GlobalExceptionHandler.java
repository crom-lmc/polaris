package com.laimengchao.sso.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器（全服务共用），统一把异常转成 {@link R}。
 * <p>
 * 401/403/404 同步 HTTP 状态码：前端 axios 依赖 401 触发重新授权，
 * 全部返回 200 会让「登录过期」变成一个普通的业务错误提示（踩过这个坑）；
 * 其余业务异常仍返 200，由前端按 code 判定。
 * 静态资源缺失接 NoResourceFoundException → 404（踩过这个坑）。
 * <p>
 * 需要启动类 scanBasePackages 包含 com.laimengchao.sso.common 才会生效。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> business(BizException e) {
        int status;
        if (e.getErrorCode() != null && e.getErrorCode().httpStatus() > 0) {
            status = e.getErrorCode().httpStatus();
        } else {
            status = switch (e.getCode()) {
                case 400 -> HttpStatus.BAD_REQUEST.value();
                case 401 -> HttpStatus.UNAUTHORIZED.value();
                case 403 -> HttpStatus.FORBIDDEN.value();
                case 404 -> HttpStatus.NOT_FOUND.value();
                case 409 -> HttpStatus.CONFLICT.value();
                default -> HttpStatus.OK.value();
            };
        }
        return ResponseEntity.status(status).body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<R<Void>> invalid(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().isEmpty()
                ? "参数错误"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(R.fail(ErrorCode.PARAM_ERROR, msg));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> noResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(R.fail(ErrorCode.NOT_FOUND));
    }

    /** 客户端参数问题：明确 400，不打 ERROR 堆栈（原来是 500 + unhandled exception 噪声）。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> missingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.PARAM_ERROR, "缺少请求参数：" + e.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Void>> badParam(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(ErrorCode.PARAM_ERROR, "请求参数格式错误：" + e.getName()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<R<Void>> badMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(R.fail(ErrorCode.PARAM_ERROR, "不支持的请求方法：" + e.getMethod()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> unknown(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(ErrorCode.SYSTEM_ERROR));
    }
}
