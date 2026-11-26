package com.laimengchao.sso.openapi.module.app.service;

import com.laimengchao.sso.openapi.module.app.vo.AppEndpointVo;
import java.util.List;

public interface AppEndpointService {

    /** 取指定第三方应用被授权调用的接口清单（含方向文案）。 */
    List<AppEndpointVo> listByApp(String appCode);
}
