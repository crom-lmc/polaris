package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.service.PartnerService;
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

/** 往来单位维护：银行账号按 contract:partner:bank:view 权限脱敏。 */
@RestController
@RequestMapping("/api/contract/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    @GetMapping
    @Permit(PermissionCodes.CONTRACT_PARTNER_LIST)
    public R<?> page(@RequestParam(required = false) String code,
                     @RequestParam(required = false) String name,
                     @RequestParam(required = false) String role,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(defaultValue = "1") int page,
                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(partnerService.page(code, name, role, status, keyword, page, size));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_PARTNER_LIST)
    public R<?> detail(@PathVariable("id") Long id) {
        return R.ok(partnerService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.CONTRACT_PARTNER_EDIT)
    public R<?> save(@RequestBody ContractRequests.PartnerSave req) {
        return R.ok(partnerService.save(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_PARTNER_EDIT)
    public R<Void> update(@PathVariable("id") Long id, @RequestBody ContractRequests.PartnerSave req) {
        partnerService.update(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/status")
    @Permit(PermissionCodes.CONTRACT_PARTNER_EDIT)
    public R<Void> toggle(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        partnerService.toggle(id, body == null ? null : body.get("status"));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_PARTNER_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        partnerService.delete(id);
        return R.ok();
    }
}
