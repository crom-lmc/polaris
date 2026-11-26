package com.laimengchao.sso.account.service;

public interface LoginLogService {

    void record(String username, Long accountId, String loginType, boolean success, String failReason, String clientIp, String userAgent);
}
