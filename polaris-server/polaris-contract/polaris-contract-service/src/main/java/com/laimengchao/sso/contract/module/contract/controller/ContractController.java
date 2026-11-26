package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.service.ContractService;
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

import java.util.Map;

/** 合同台账：采购 / 销售共用；金额与执行进度从清单及付款计划派生。 */
@RestController
@RequestMapping("/api/contract/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    @Permit(PermissionCodes.CONTRACT_LEDGER_LIST)
    public R<?> page(@RequestParam(required = false) String type,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String projectNo,
                     @RequestParam(required = false) String partnerCode,
                     @RequestParam(defaultValue = "1") int page,
                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(contractService.page(type, status, keyword, projectNo, partnerCode, page, size));
    }

    @GetMapping("/stats")
    @Permit(PermissionCodes.CONTRACT_LEDGER_LIST)
    public R<?> stats(@RequestParam(required = false) String type) {
        return R.ok(contractService.stats(type));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_LEDGER_LIST)
    public R<?> detail(@PathVariable("id") Long id) {
        return R.ok(contractService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.CONTRACT_LEDGER_EDIT)
    public R<?> save(@RequestBody ContractRequests.ContractSave req) {
        return R.ok(contractService.save(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_LEDGER_EDIT)
    public R<Void> update(@PathVariable("id") Long id, @RequestBody ContractRequests.ContractSave req) {
        contractService.update(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/status")
    @Permit(PermissionCodes.CONTRACT_LEDGER_EDIT)
    public R<Void> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        contractService.changeStatus(id, body == null ? null : body.get("status"));
        return R.ok();
    }

    @GetMapping("/{id}/changes")
    @Permit(PermissionCodes.CONTRACT_LEDGER_LIST)
    public R<?> changeLogs(@PathVariable("id") Long id) {
        return R.ok(contractService.changeLogs(id));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_LEDGER_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        contractService.delete(id);
        return R.ok();
    }
}
