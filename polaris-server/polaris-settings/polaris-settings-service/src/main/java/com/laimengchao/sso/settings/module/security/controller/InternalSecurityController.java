package com.laimengchao.sso.settings.module.security.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.module.security.service.SecurityConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨服务读取安全配置开关（X-Internal-Token 准入，不经网关）。
 * 目前只暴露演示模式：sso-auth 登录前判断演示账号是否允许登录。
 */
@RestController
@RequestMapping("/internal/security")
public class InternalSecurityController {

    private final SecurityConfigService securityConfigService;

    public InternalSecurityController(SecurityConfigService securityConfigService) {
        this.securityConfigService = securityConfigService;
    }

    /** 演示模式开关：true 开启（演示账号可登录）/ false 关闭 */
    @GetMapping("/demo-mode")
    public R<Boolean> demoMode() {
        return R.ok(securityConfigService.isDemoModeEnabled());
    }
}
