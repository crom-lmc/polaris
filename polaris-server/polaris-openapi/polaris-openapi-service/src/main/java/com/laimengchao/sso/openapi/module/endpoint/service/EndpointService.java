package com.laimengchao.sso.openapi.module.endpoint.service;

import com.laimengchao.sso.openapi.module.endpoint.entity.ApiOpenEndpoint;
import java.util.List;

public interface EndpointService {

    List<ApiOpenEndpoint> list();

    /** 新建或按 api_code 更新。 */
    void save(ApiOpenEndpoint req);

    void toggle(String apiCode, int status);

    void delete(String apiCode);
}
