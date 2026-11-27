package com.laimengchao.sso.contract.internal;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.contract.module.contract.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.contract.module.contract.service.ContractService;
import com.laimengchao.sso.contract.module.contract.service.PaymentPlanService;
import com.laimengchao.sso.contract.module.contract.service.PurchasePlanService;
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
 * settings 投递器只认 2xx：真异常回 5xx 触发重试，「已处理过/未识别」回 200。
 */
@Slf4j
@RestController
@RequestMapping("/internal/contract")
@RequiredArgsConstructor
public class InternalApprovalCallbackController {

    private static final String BIZ_PURCHASE = "contract.purchase";
    private static final String BIZ_PURCHASE_SENIOR = "contract.purchase.senior";
    private static final String BIZ_PAYMENT = "contract.payment";
    private static final String BIZ_PAYMENT_SENIOR = "contract.payment.senior";
    private static final String BIZ_SALES = "contract.sales";
    private static final String BIZ_PLAN = "contract.plan";

    private final ContractService contractService;
    private final PaymentPlanService paymentPlanService;
    private final PurchasePlanService purchasePlanService;

    @PostMapping("/approval-callback")
    public ResponseEntity<R<Void>> approvalCallback(@RequestBody ApprovalCallbackRequest req) {
        log.info("收到合同审批终审回调 instance={} bizType={} bizId={} result={}",
                req.getInstanceId(), req.getBizType(), req.getBizId(), req.getResult());
        try {
            if (req.getBizId() == null) {
                log.warn("合同审批回调缺 bizType/bizId：{}", req.getBizType());
            } else if (BIZ_PURCHASE.equals(req.getBizType()) || BIZ_PURCHASE_SENIOR.equals(req.getBizType())
                    || BIZ_SALES.equals(req.getBizType())) {
                contractService.handleApprovalResult(req.getBizId(), req.getApproved(), req.getResult());
            } else if (BIZ_PAYMENT.equals(req.getBizType()) || BIZ_PAYMENT_SENIOR.equals(req.getBizType())) {
                paymentPlanService.handleApprovalResult(req.getBizId(), req.getApproved(), req.getResult());
            } else if (BIZ_PLAN.equals(req.getBizType())) {
                purchasePlanService.handleApprovalResult(req.getBizId(), req.getApproved(), req.getResult());
            } else {
                log.warn("合同审批回调 bizType 未识别：{}", req.getBizType());
            }
            return ResponseEntity.ok(R.ok());
        } catch (Exception e) {
            log.error("处理合同审批回调失败 instance={} bizId={}", req.getInstanceId(), req.getBizId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(R.fail(5000, "回调处理失败"));
        }
    }
}
