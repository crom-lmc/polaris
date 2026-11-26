package com.laimengchao.sso.ehr.security;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;


public interface DataScopeService {

    String currentScopeCode();

    String scopeCodeOf(Long accountId);

    <T> void applyOrgScope(LambdaQueryWrapper<T> wrapper, SFunction<T, String> orgCodeFn);

    /** 单条记录是否可见（返回 boolean，detail/export 用） */
    boolean canAccessOrg(String orgCode);

    /** 单条记录是否可见（抛 Forbidden，OrgService CRUD 用） */
    void assertCanAccessOrg(String orgCode);

    /** 是否有敏感字段明文权限（独立的按钮级权限，和数据范围无关） */
    boolean canRevealSensitive();

    boolean hasPermission(String code);
}
