package com.laimengchao.sso.contract.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.contract.module.contract.dto.ContractRequests;
import com.laimengchao.sso.contract.module.contract.service.ProjectService;
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

/** 项目档案：单号 PRJ-yyyyMMdd-XXXXXX，状态 DRAFT→ACTIVE→PAUSED→ACTIVE。 */
@RestController
@RequestMapping("/api/contract/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @Permit(PermissionCodes.CONTRACT_PROJECT_LIST)
    public R<?> page(@RequestParam(required = false) String code,
                     @RequestParam(required = false) String name,
                     @RequestParam(required = false) String type,
                     @RequestParam(required = false) String status,
                     @RequestParam(defaultValue = "1") int page,
                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(projectService.page(code, name, type, status, page, size));
    }

    @GetMapping("/options")
    @Permit(PermissionCodes.CONTRACT_PROJECT_LIST)
    public R<?> options() {
        return R.ok(projectService.options());
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_PROJECT_LIST)
    public R<?> detail(@PathVariable("id") Long id) {
        return R.ok(projectService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.CONTRACT_PROJECT_EDIT)
    public R<?> save(@RequestBody ContractRequests.ProjectSave req) {
        return R.ok(projectService.save(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_PROJECT_EDIT)
    public R<Void> update(@PathVariable("id") Long id, @RequestBody ContractRequests.ProjectSave req) {
        projectService.update(id, req);
        return R.ok();
    }

    @PostMapping("/{id}/status")
    @Permit(PermissionCodes.CONTRACT_PROJECT_EDIT)
    public R<Void> changeStatus(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        projectService.changeStatus(id, body == null ? null : body.get("status"));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_PROJECT_EDIT)
    public R<Void> delete(@PathVariable("id") Long id) {
        projectService.delete(id);
        return R.ok();
    }
}
