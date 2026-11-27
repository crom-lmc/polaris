package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.service.PurchasePlanService;
import com.laimengchao.sso.contract.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 采购计划：CGJH-yyyy-XXXXXX，审批通过后转采购合同并带入清单。 */
@RestController
@RequestMapping("/api/contract/purchase-plans")
@RequiredArgsConstructor
public class PurchasePlanController {

    private final PurchasePlanService planService;

    @GetMapping
    @Permit(PermissionCodes.CONTRACT_PLAN_LIST)
    public R<?> page(@RequestParam(required = false) String no,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(defaultValue = "1") int page,
                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(planService.page(no, status, keyword, page, size));
    }

    /** 发起采购计划的默认申请人/需求部门：服务端从 EHR 反查，避免前端雪花 ID 精度丢失 */
    @GetMapping("/my-profile")
    @Permit(PermissionCodes.CONTRACT_PLAN_LIST)
    public R<?> myProfile() {
        return R.ok(planService.myProfile());
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_PLAN_LIST)
    public R<?> detail(@PathVariable("id") Long id) {
        return R.ok(planService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.CONTRACT_PLAN_EDIT)
    public R<?> save(@RequestBody ContractRequests.PurchasePlanSave req) {
        return R.ok(planService.save(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_PLAN_EDIT)
    public R<Void> update(@PathVariable("id") Long id, @RequestBody ContractRequests.PurchasePlanSave req) {
        planService.update(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @Permit(PermissionCodes.CONTRACT_PLAN_EDIT)
    public R<Void> submit(@PathVariable("id") Long id) {
        planService.submit(id);
        return R.ok();
    }

    /** 审批信息只读查询（走审批中心反查，未发起返回 null） */
    @GetMapping("/{id}/approval")
    @Permit(PermissionCodes.CONTRACT_PLAN_LIST)
    public R<?> approvalInfo(@PathVariable("id") Long id) {
        return R.ok(planService.approvalInfo(id));
    }

    @PostMapping("/{id}/approve")
    @Permit(PermissionCodes.CONTRACT_PLAN_EDIT)
    public R<Void> approve(@PathVariable("id") Long id) {
        planService.approve(id);
        return R.ok();
    }

    @PostMapping("/{id}/reject")
    @Permit(PermissionCodes.CONTRACT_PLAN_EDIT)
    public R<Void> reject(@PathVariable("id") Long id) {
        planService.reject(id);
        return R.ok();
    }

    @PostMapping("/{id}/convert")
    @Permit(PermissionCodes.CONTRACT_PLAN_EDIT)
    public R<?> convert(@PathVariable("id") Long id, @RequestBody ContractRequests.PlanConvertRequest req) {
        return R.ok(planService.convert(id, req));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_PLAN_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        planService.delete(id);
        return R.ok();
    }
}
