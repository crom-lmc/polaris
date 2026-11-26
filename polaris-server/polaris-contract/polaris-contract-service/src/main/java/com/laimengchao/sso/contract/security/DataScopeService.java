package com.laimengchao.sso.contract.security;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;


public interface DataScopeService {

    String currentScopeCode();

    String scopeCodeOf(Long accountId);

    /**
     * 把数据范围拼进任意业务表的 LambdaQueryWrapper。
     * 空 scopeCode（未配置）→ WHERE org_code = '__NO_ACCESS__'（安全兜底空集）。
     */
    <T> void applyOrgScope(LambdaQueryWrapper<T> wrapper, SFunction<T, String> orgCodeFn);

    /**
     * 用显式 scopeCode 拼数据范围：internal Feign 无登录上下文时，由调用方传入账号解析出的范围。
     * 空范围同样 fail-closed 写 {@code __NO_ACCESS__}。
     */
    <T> void applyOrgScope(LambdaQueryWrapper<T> wrapper, SFunction<T, String> orgCodeFn, String scopeCode);

    /** 单条记录是否可见（返回 boolean，只读选项用） */
    boolean canAccessOrg(String orgCode);

    /** 单条记录是否可见（抛 Forbidden，写操作与详情用） */
    void assertCanAccessOrg(String orgCode);

    /** 是否有指定权限码（敏感字段明文等独立按钮权限，与数据范围无关） */
    boolean hasPermission(String code);
}
