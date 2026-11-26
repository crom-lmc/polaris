package com.laimengchao.sso.cms.module.portal.service;

import com.laimengchao.sso.cms.module.portal.entity.PortalLayout;
import java.util.List;

public interface PortalLayoutService {

    /** 首次启动（表为空）时写入默认模块，避免门户空白 */
    void initDefaults();

    /** 管理端：全部模块（含隐藏），按 sort 排 */
    List<PortalLayout> listAll();

    /** 门户端：仅启用模块，按 sort 排 */
    List<PortalLayout> listEnabled();

    /**
     * 保存布局：按 moduleKey 幂等 upsert，sort 以传入顺序重排（1..n）。
     * 不删除未出现的模块（模块集合是固定的），避免误删配置。
     */
    List<PortalLayout> saveLayout(List<PortalLayout> items);
}
