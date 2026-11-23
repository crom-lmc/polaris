package com.laimengchao.sso.openapi.module.authz.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.openapi.module.authz.service.AuthzService;
import com.laimengchao.sso.openapi.module.authz.vo.AuthzGrantRequest;
import com.laimengchao.sso.openapi.module.authz.vo.AuthzRevokeRequest;
import com.laimengchao.sso.openapi.module.authz.vo.AuthzToggleRequest;
import com.laimengchao.sso.openapi.module.authz.vo.AuthzVo;
import com.laimengchao.sso.openapi.security.admin.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 应用授权管理接口（管理端）。复用「配置接口授权」权限码 openapi:app:auth，
 * 与统一登录中心「对接应用」的授权语义一致，避免新增权限码带来的数据/角色迁移。
 */
@RestController
@RequestMapping("/api/open/admin/authz")
@RequiredArgsConstructor
public class AuthzAdminController {

    private final AuthzService authzService;

    @GetMapping("/list")
    @Permit(PermissionCodes.OPENAPI_APP_AUTH)
    public R<List<AuthzVo>> list() {
        return R.ok(authzService.list());
    }

    @PostMapping("/grant")
    @Permit(PermissionCodes.OPENAPI_APP_AUTH)
    public R<Map<String, Integer>> grant(@RequestBody AuthzGrantRequest req) {
        return R.ok(Map.of("created", authzService.grant(req.getAppCode(), req.getApiCodes())));
    }

    @PostMapping("/revoke")
    @Permit(PermissionCodes.OPENAPI_APP_AUTH)
    public R<Map<String, Integer>> revoke(@RequestBody AuthzRevokeRequest req) {
        return R.ok(Map.of("deleted", authzService.revoke(req.getAppCode(), req.getApiCode())));
    }

    @PostMapping("/toggle")
    @Permit(PermissionCodes.OPENAPI_APP_AUTH)
    public R<Map<String, Integer>> toggle(@RequestBody AuthzToggleRequest req) {
        return R.ok(Map.of("updated", authzService.toggle(req.getAppCode(), req.getApiCode(), req.getStatus())));
    }
}
