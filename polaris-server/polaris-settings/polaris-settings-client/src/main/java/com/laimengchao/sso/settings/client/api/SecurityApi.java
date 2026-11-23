package com.laimengchao.sso.settings.client.api;

import com.laimengchao.sso.settings.client.config.ApprovalFeignConfig;
import com.laimengchao.sso.settings.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 安全配置内部接口：sso-auth 登录前判断演示模式是否开启。
 * <p>
 * 走 {@code /internal/security/**}（不经网关），凭 X-Internal-Token 准入。
 */
@FeignClient(
        name = "settings",
        contextId = "settings-security",
        url = "${settings.client.base-url:http://127.0.0.1:9007}",
        configuration = ApprovalFeignConfig.class)
public interface SecurityApi {

    @GetMapping("/internal/security/demo-mode")
    R<Boolean> demoMode();
}
