package com.laimengchao.sso.openapi.module.setting.controller;

import com.laimengchao.sso.openapi.module.setting.service.impl.SettingServiceImpl;
import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.dto.AppInfo;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.openapi.module.setting.entity.ApiOpenSetting;
import com.laimengchao.sso.openapi.module.setting.service.SettingService;
import com.laimengchao.sso.openapi.module.setting.vo.AppIpWhitelistVo;
import com.laimengchao.sso.openapi.security.admin.Permit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 平台设置管理接口（SSO 用户令牌 + @Permit）。 */
@Slf4j
@RestController
@RequestMapping("/api/open/admin/setting")
@RequiredArgsConstructor
public class SettingAdminController {

    private static final String APP_TYPE_OPENAPI_CLIENT = "OPENAPI_CLIENT";

    private final SettingService settingService;
    private final AccountApi accountApi;

    @GetMapping("/list")
    @Permit(PermissionCodes.OPENAPI_SETTING_EDIT)
    public com.laimengchao.sso.common.R<List<ApiOpenSetting>> list() {
        return com.laimengchao.sso.common.R.ok(settingService.list());
    }

    /** 保存单个配置项：{@code {"key":"ip.whitelist","value":"1.2.3.4\n10.0.0.0/8"}} */
    @PostMapping("/save")
    @Permit(PermissionCodes.OPENAPI_SETTING_EDIT)
    public com.laimengchao.sso.common.R<Void> save(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "key 必填");
        }
        settingService.save(key, body.get("value"), body.get("remark"));
        return com.laimengchao.sso.common.R.ok();
    }

    /**
     * 按应用列出来源 IP 白名单：全部启用中的第三方应用 + 各自应用级白名单 + 当前生效来源。
     * 应用级未配置时 effectiveFrom=GLOBAL（回退全局），全局也空则 NONE（不限制）。
     */
    @GetMapping("/ip-whitelist")
    @Permit(PermissionCodes.OPENAPI_SETTING_EDIT)
    public com.laimengchao.sso.common.R<List<AppIpWhitelistVo>> ipWhitelist() {
        R<List<AppInfo>> resp;
        try {
            resp = accountApi.listEnabledAppsByType(APP_TYPE_OPENAPI_CLIENT);
        } catch (Exception e) {
            log.error("查询第三方应用失败", e);
            throw new BizException(ErrorCode.BIZ_ERROR, "查询第三方应用失败");
        }
        if (resp == null || !resp.ok()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "查询第三方应用失败");
        }
        String global = settingService.get(SettingServiceImpl.KEY_IP_WHITELIST, "");
        List<AppIpWhitelistVo> out = new ArrayList<>();
        for (AppInfo app : resp.getData() == null ? List.<AppInfo>of() : resp.getData()) {
            if (app.getCode() == null || app.getCode().isBlank()) {
                continue;
            }
            String own = settingService.get(SettingService.keyIpWhitelist(app.getCode()), "");
            AppIpWhitelistVo vo = new AppIpWhitelistVo();
            vo.setAppCode(app.getCode());
            vo.setAppName(app.getName());
            vo.setWhitelist(own == null ? "" : own);
            vo.setEffectiveFrom(own != null && !own.isBlank() ? "APP"
                    : (global != null && !global.isBlank() ? "GLOBAL" : "NONE"));
            out.add(vo);
        }
        return com.laimengchao.sso.common.R.ok(out);
    }

    /** 保存指定第三方应用的来源 IP 白名单：{@code {"appCode":"partner_xxx","value":"1.2.3.4\n10.0.0.0/8"}} */
    @PostMapping("/ip-whitelist/save")
    @Permit(PermissionCodes.OPENAPI_SETTING_EDIT)
    public com.laimengchao.sso.common.R<Void> saveIpWhitelist(@RequestBody Map<String, String> body) {
        String appCode = body.get("appCode");
        if (appCode == null || appCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }
        settingService.save(SettingService.keyIpWhitelist(appCode.trim()), body.get("value"),
                "第三方应用 " + appCode.trim() + " 的来源 IP 白名单");
        return com.laimengchao.sso.common.R.ok();
    }
}
