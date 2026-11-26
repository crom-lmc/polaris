package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.service.MaterialService;
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

/** 物料三级树：编码由 Service 按父级续位，不进 cfg_code_rule。 */
@RestController
@RequestMapping("/api/contract/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    @Permit(PermissionCodes.CONTRACT_MATERIAL_LIST)
    public R<?> tree() {
        return R.ok(materialService.tree());
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_LIST)
    public R<?> detail(@PathVariable("id") Long id) {
        return R.ok(materialService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.CONTRACT_MATERIAL_EDIT)
    public R<?> save(@RequestBody ContractRequests.MaterialSave req) {
        return R.ok(materialService.save(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_EDIT)
    public R<Void> update(@PathVariable("id") Long id, @RequestBody ContractRequests.MaterialSave req) {
        materialService.update(id, req);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_MATERIAL_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        materialService.delete(id);
        return R.ok();
    }
}
