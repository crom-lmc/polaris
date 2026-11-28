package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.service.ContractItemService;
import com.laimengchao.sso.contract.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 合同清单与到货验收：清单保存前做项目预算校验。 */
@RestController
@RequestMapping("/api/contract/contracts/{contractNo}/items")
@RequiredArgsConstructor
public class ContractItemController {

    private final ContractItemService itemService;

    @GetMapping
    @Permit(PermissionCodes.CONTRACT_LEDGER_LIST)
    public R<?> list(@PathVariable("contractNo") String contractNo) {
        return R.ok(itemService.list(contractNo));
    }

    @PostMapping
    @Permit(PermissionCodes.CONTRACT_ITEM_EDIT)
    public R<?> save(@PathVariable("contractNo") String contractNo,
                     @RequestBody ContractRequests.ItemSave req) {
        return R.ok(itemService.save(contractNo, req));
    }

    @GetMapping("/acceptances")
    @Permit(PermissionCodes.CONTRACT_LEDGER_LIST)
    public R<?> acceptances(@PathVariable("contractNo") String contractNo) {
        return R.ok(itemService.acceptances(contractNo));
    }

    @PostMapping("/{id}/accept")
    @Permit(PermissionCodes.CONTRACT_ITEM_EDIT)
    public R<?> accept(@PathVariable("contractNo") String contractNo,
                       @PathVariable("id") Long id,
                       @RequestBody ContractRequests.AcceptRequest req) {
        return R.ok(itemService.accept(id, req));
    }

    @GetMapping("/signs")
    @Permit(PermissionCodes.CONTRACT_LEDGER_LIST)
    public R<?> signs(@PathVariable("contractNo") String contractNo) {
        return R.ok(itemService.signs(contractNo));
    }

    /** 到货签收：独立权限码；仅累加 signedQty，不作为验收单 */
    @PostMapping("/{id}/sign")
    @Permit(PermissionCodes.CONTRACT_SIGN_EDIT)
    public R<?> sign(@PathVariable("contractNo") String contractNo,
                     @PathVariable("id") Long id,
                     @RequestBody ContractRequests.SignRequest req) {
        return R.ok(itemService.sign(id, req));
    }

    /** 销售验收推应收失败后重试（幂等；已有 arNo 直接返回） */
    @PostMapping("/acceptances/{acceptanceId}/push-ar")
    @Permit(PermissionCodes.CONTRACT_ITEM_EDIT)
    public R<Void> pushAr(@PathVariable("contractNo") String contractNo,
                          @PathVariable("acceptanceId") Long acceptanceId) {
        itemService.pushReceivable(acceptanceId);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_ITEM_EDIT)
    public R<Void> delete(@PathVariable("contractNo") String contractNo, @PathVariable("id") Long id) {
        itemService.delete(id);
        return R.ok();
    }
}
