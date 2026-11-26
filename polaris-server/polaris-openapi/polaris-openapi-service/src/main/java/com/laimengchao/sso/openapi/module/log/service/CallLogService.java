package com.laimengchao.sso.openapi.module.log.service;

import com.laimengchao.sso.openapi.module.log.entity.ApiCallLog;

public interface CallLogService {

    void save(ApiCallLog logEntry);
}
