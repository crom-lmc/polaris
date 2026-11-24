package com.laimengchao.sso.audit.module.audit.controller;

import com.laimengchao.sso.audit.module.audit.service.SystemLogService;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.logging.SystemLogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统日志内部接收端：各服务 Logback Appender 经 /internal/logs 异步批量上报。
 * <p>
 * 与 /internal/audit/** 一样由 common 的 InternalTokenInterceptor 做 fail-closed 准入（X-Internal-Token）。
 * 接收端自身不记录访问日志，避免「记录日志的日志」自我循环。
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalSystemLogController {

    private final SystemLogService systemLogService;

    @PostMapping("/logs")
    public R<Void> report(@RequestBody List<SystemLogEvent> events) {
        systemLogService.recordBatch(events);
        return R.ok();
    }
}
