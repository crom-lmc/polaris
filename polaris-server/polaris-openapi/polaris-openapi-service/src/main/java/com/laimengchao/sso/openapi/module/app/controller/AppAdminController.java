package com.laimengchao.sso.openapi.module.app.controller;

import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.dto.AppInfo;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.app.service.AppEndpointService;
import com.laimengchao.sso.openapi.module.app.service.ClientAppService;
import com.laimengchao.sso.openapi.module.app.vo.AppEndpointVo;
import com.laimengchao.sso.openapi.module.app.vo.AppOptionVo;
import com.laimengchao.sso.openapi.module.app.vo.ClientAppSaveRequest;
import com.laimengchao.sso.openapi.module.app.vo.ClientAppVo;
import com.laimengchao.sso.openapi.security.admin.Permit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对接应用管理接口（SSO 用户令牌 + @Permit）。
 * <p>
 * 第三方应用主数据与授权全部落开放平台本地库（polaris_openapi），
 * 由 {@link ClientAppService} 统一维护，不再转发到统一登录中心。
 */
@Slf4j
@RestController
@RequestMapping("/api/open/admin/app")
@RequiredArgsConstructor
public class AppAdminController {

    private final AccountApi accountApi;
    private final ClientAppService clientAppService;
    private final AppEndpointService appEndpointService;

    /** 对接应用清单（含停用），供管理端自助维护 */
    @GetMapping("/list")
    @Permit(PermissionCodes.OPENAPI_APP_LIST)
    public com.laimengchao.sso.common.R<List<ClientAppVo>> list() {
        return com.laimengchao.sso.common.R.ok(clientAppService.list());
    }

    /** 新增第三方对接应用，返回明文密钥（仅此一次） */
    @PostMapping("/create")
    @Permit(PermissionCodes.OPENAPI_APP_CREATE)
    public com.laimengchao.sso.common.R<ClientAppVo> create(@RequestBody ClientAppSaveRequest req) {
        return com.laimengchao.sso.common.R.ok(clientAppService.create(req));
    }

    /** 编辑第三方对接应用（编码不可改） */
    @PostMapping("/update")
    @Permit(PermissionCodes.OPENAPI_APP_EDIT)
    public com.laimengchao.sso.common.R<ClientAppVo> update(@RequestBody ClientAppSaveRequest req) {
        return com.laimengchao.sso.common.R.ok(clientAppService.update(req));
    }

    /** 启停第三方对接应用 */
    @PostMapping("/toggle")
    @Permit(PermissionCodes.OPENAPI_APP_TOGGLE)
    public com.laimengchao.sso.common.R<Void> toggle(@RequestParam String appCode, @RequestParam Integer status) {
        clientAppService.toggle(appCode, status);
        return com.laimengchao.sso.common.R.ok(null);
    }

    /** 重置密钥，返回新的明文密钥（仅此一次） */
    @PostMapping("/reset-secret")
    @Permit(PermissionCodes.OPENAPI_APP_SECRET_RESET)
    public com.laimengchao.sso.common.R<String> resetSecret(@RequestParam String appCode) {
        return com.laimengchao.sso.common.R.ok(clientAppService.resetSecret(appCode));
    }

    /** 查看第三方对接应用密钥明文（存量应用未记录明文时返回空串） */
    @GetMapping("/secret")
    @Permit(PermissionCodes.OPENAPI_APP_SECRET_RESET)
    public com.laimengchao.sso.common.R<String> viewSecret(@RequestParam String appCode) {
        return com.laimengchao.sso.common.R.ok(clientAppService.viewSecret(appCode));
    }

    /** 删除第三方对接应用（含清理授权） */
    @PostMapping("/delete")
    @Permit(PermissionCodes.OPENAPI_APP_DELETE)
    public com.laimengchao.sso.common.R<Void> delete(@RequestParam String appCode) {
        clientAppService.delete(appCode);
        return com.laimengchao.sso.common.R.ok(null);
    }

    /** 全部启用中的应用（API 归属应用下拉用，含 settings/ehr/email 等内部系统） */
    @GetMapping("/options")
    @Permit(PermissionCodes.OPENAPI_ENDPOINT_LIST)
    public com.laimengchao.sso.common.R<List<AppOptionVo>> options() {
        R<List<AppInfo>> resp;
        try {
            resp = accountApi.listEnabledApps();
        } catch (Exception e) {
            log.error("查询应用下拉列表失败", e);
            throw new BizException(OpenapiErrorCode.CHANNEL_ERROR, "查询应用下拉列表失败");
        }
        if (resp == null || !resp.ok()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "查询应用下拉列表失败");
        }
        List<AppOptionVo> options = resp.getData() == null ? List.of()
                : resp.getData().stream().map(a -> {
                    AppOptionVo vo = new AppOptionVo();
                    vo.setCode(a.getCode());
                    vo.setName(a.getName());
                    return vo;
                }).toList();
        return com.laimengchao.sso.common.R.ok(options);
    }

    /** 指定第三方应用被授权调用的接口清单（按该应用域下的 inbound:/outbound: 权限码匹配） */
    @GetMapping("/{appCode}/endpoints")
    @Permit(PermissionCodes.OPENAPI_APP_LIST)
    public com.laimengchao.sso.common.R<List<AppEndpointVo>> endpoints(@PathVariable String appCode) {
        return com.laimengchao.sso.common.R.ok(appEndpointService.listByApp(appCode));
    }
}
