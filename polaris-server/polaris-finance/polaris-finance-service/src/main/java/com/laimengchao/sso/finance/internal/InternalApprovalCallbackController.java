package com.laimengchao.sso.finance.internal;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.finance.module.finance.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.finance.module.finance.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批中心终审结果接收端（走 /internal/**，由 X-Internal-Token 准入，不经网关）。
 *
 * <p>settings 投递器只认 2xx：真异常必须回 5xx 触发退避重试；「已处理过/不是当前单」
 * 属正常完成，回 200，避免重复投递反复重试。
 */
@Slf4j
@RestController
@RequestMapping("/internal/finance")
@RequiredArgsConstructor
public class InternalApprovalCallbackController {

    private static final String BIZ_EXPENSE = "finance.expense";
    private static final String BIZ_EXPENSE_SENIOR = "finance.expense.senior";

    private final ExpenseService expenseService;

    @PostMapping("/approval-callback")
    public ResponseEntity<R<Void>> approvalCallback(@RequestBody ApprovalCallbackRequest req) {
        log.info("收到财务审批终审回调 instance={} bizType={} bizId={} result={}",
                req.getInstanceId(), req.getBizType(), req.getBizId(), req.getResult());
        try {
            if ((BIZ_EXPENSE.equals(req.getBizType()) || BIZ_EXPENSE_SENIOR.equals(req.getBizType()))
                    && req.getBizId() != null) {
                expenseService.handleApprovalResult(req.getBizId(), req.getApproved(), req.getResult());
            } else {
                log.warn("财务审批回调 bizType 未识别：{}", req.getBizType());
            }
            return ResponseEntity.ok(R.ok());
        } catch (Exception e) {
            log.error("处理财务审批回调失败 instance={} bizId={}", req.getInstanceId(), req.getBizId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(R.fail(5000, "回调处理失败"));
        }
    }
}
