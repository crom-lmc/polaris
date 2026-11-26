package com.laimengchao.sso.job.module.job.service;

public interface JobLeaderService {

    void afterPropertiesSet();

    boolean isLeader();

    String owner();

    void destroy();
}
