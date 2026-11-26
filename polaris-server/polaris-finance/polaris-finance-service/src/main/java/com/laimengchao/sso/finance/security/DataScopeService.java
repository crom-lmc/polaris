package com.laimengchao.sso.finance.security;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;


public interface DataScopeService {

    String currentScopeCode();

    String scopeCodeOf(Long accountId);

    /**
     * 把数据范围拼进任意业务单据的 LambdaQueryWrapper。
     * 空 scopeCode → WHERE org_code = '__NO_ACCESS__'（空集，fail-closed）。
     */
    <T> void applyOrgScope(LambdaQueryWrapper<T> wrapper, SFunction<T, String> orgCodeFn);

    /** 当前记录是否可见 */
    boolean canAccessOrg(String orgCode);

    /** 单条记录校验：不可见直接抛 403 */
    void assertCanAccessOrg(String orgCode);

    /** 薪资明文字段权限：独立于数据范围，默认脱敏 */
    boolean canRevealSalary();

    /** 新建单据时归属组织：优先取账号数据范围根，其次取入参 */
    String orgCodeOf(String requested);
}
