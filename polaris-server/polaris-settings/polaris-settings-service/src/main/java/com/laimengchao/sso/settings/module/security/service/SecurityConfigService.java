package com.laimengchao.sso.settings.module.security.service;

import com.laimengchao.sso.settings.module.security.dto.SecurityConfigRequest;
import com.laimengchao.sso.settings.module.security.entity.SecurityConfig;

public interface SecurityConfigService {

    /** 读取配置；首行缺失时插入空行，其余字段由 DDL 的 DEFAULT 提供 */
    SecurityConfig get();

    /** 演示模式开关（供 sso-auth 跨服读取）；未开启或配置缺失一律按关闭处理 */
    boolean isDemoModeEnabled();

    /** 保存并发回最新配置 */
    SecurityConfig save(SecurityConfigRequest req);
}
