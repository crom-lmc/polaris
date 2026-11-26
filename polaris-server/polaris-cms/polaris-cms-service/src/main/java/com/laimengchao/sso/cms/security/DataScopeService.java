package com.laimengchao.sso.cms.security;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;


public interface DataScopeService {

    String currentScopeCode();

    String scopeCodeOf(Long accountId);

    /** 把数据范围拼进任意业务单据的 LambdaQueryWrapper */
    <T> void applyOrgScope(LambdaQueryWrapper<T> wrapper, SFunction<T, String> orgCodeFn);

    boolean canAccessOrg(String orgCode);

    void assertCanAccessOrg(String orgCode);
}
