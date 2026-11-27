package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.service.PaymentPlanService;
import com.laimengchao.sso.contract.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 合同付款计划：发起申请不生成 YS/FK 单号，由财务域建单后回写。 */
@RestController
@RequestMapping("/api/contract/payment-plans")
@RequiredArgsConstructor
public class PaymentPlanController {

    private final PaymentPlanService planService;

    @GetMapping("/contract/{contractNo}")
    @Permit(PermissionCodes.CONTRACT_PAYMENT_LIST)
    public R<?> list(@PathVariable("contractNo") String contractNo) {
        return R.ok(planService.list(contractNo));
    }

    @PostMapping("/contract/{contractNo}")
    @Permit(PermissionCodes.CONTRACT_PAYMENT_EDIT)
    public R<?> save(@PathVariable("contractNo") String contractNo,
                     @RequestBody ContractRequests.PaymentPlanSave req) {
        return R.ok(planService.save(contractNo, req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_PAYMENT_EDIT)
    public R<Void> update(@PathVariable("id") Long id,
                          @RequestBody ContractRequests.PaymentPlanSave req) {
        planService.update(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/apply")
    @Permit(PermissionCodes.CONTRACT_PAYMENT_EDIT)
    public R<?> apply(@PathVariable("id") Long id,
                      @RequestBody(required = false) ContractRequests.PaymentApplyRequest req) {
        return R.ok(planService.apply(id, req));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_PAYMENT_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        planService.delete(id);
        return R.ok();
    }

    /** 已发起审批的付款计划查看审批信息（复用 settings 审批中心；未发起时 data=null） */
    @GetMapping("/{id}/approval")
    @Permit(PermissionCodes.CONTRACT_PAYMENT_LIST)
    public R<?> approvalInfo(@PathVariable("id") Long id) {
        return R.ok(planService.approvalInfo(id));
    }
}
