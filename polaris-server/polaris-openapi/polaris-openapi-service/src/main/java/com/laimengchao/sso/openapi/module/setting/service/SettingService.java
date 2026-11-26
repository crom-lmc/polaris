package com.laimengchao.sso.openapi.module.setting.service;

import com.laimengchao.sso.openapi.module.setting.entity.ApiOpenSetting;
import com.laimengchao.sso.openapi.module.setting.service.impl.SettingServiceImpl;

import java.util.List;

public interface SettingService {

    /** 启动时把已持久化的脱敏规则应用到 MaskUtils。 */
    void init();

    /** 读取配置值；不存在返回空串。 */
    String get(String key, String defaultValue);

    /** 保存或更新配置（按 setting_key 幂等）。 */
    void save(String key, String value, String remark);

    /** 列出全部设置。 */
    List<ApiOpenSetting> list();

    /** 指定应用的白名单配置键。 */
    static String keyIpWhitelist(String appCode) {
        return SettingServiceImpl.keyIpWhitelist(appCode);
    }
}
