package com.laimengchao.sso.finance.security.impl;

import com.laimengchao.sso.finance.security.DataScopeService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.DataScope;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 财务域数据权限服务：scopeCode（账号级根组织编码，支持逗号多值）按 org_code 前缀匹配。
 * 空范围一律 fail-closed，返回空集；单条记录用 assertCanAccessOrg 二次校验。
 */
@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScopeService, DataScope {

    private final ScopeCodeCache scopeCodeCache;

    @Override
    public String currentScopeCode() {
        Long accountId = AccountContext.getAccountId();
        return accountId == null ? null : scopeCodeCache.get(accountId);
    }

    @Override
    public String scopeCodeOf(Long accountId) {
        return scopeCodeCache.get(accountId);
    }

    /**
     * 把数据范围拼进任意业务单据的 LambdaQueryWrapper。
     * 空 scopeCode → WHERE org_code = '__NO_ACCESS__'（空集，fail-closed）。
     */
    public <T> void applyOrgScope(LambdaQueryWrapper<T> wrapper, SFunction<T, String> orgCodeFn) {
        String scopeCode = currentScopeCode();
        if (scopeCode == null || scopeCode.isBlank()) {
            wrapper.eq(orgCodeFn, "__NO_ACCESS__");
            return;
        }
        String[] roots = scopeCode.split(",");
        if (roots.length == 1) {
            wrapper.likeRight(orgCodeFn, roots[0].trim());
            return;
        }
        wrapper.and(w -> {
            boolean first = true;
            for (String root : roots) {
                String r = root.trim();
                if (r.isEmpty()) {
                    continue;
                }
                if (first) {
                    w.likeRight(orgCodeFn, r);
                    first = false;
                } else {
                    w.or().likeRight(orgCodeFn, r);
                }
            }
        });
    }

    /** 当前记录是否可见 */
    public boolean canAccessOrg(String orgCode) {
        return DataScope.super.canAccessOrg(orgCode);
    }

    /** 单条记录校验：不可见直接抛 403 */
    public void assertCanAccessOrg(String orgCode) {
        if (!canAccessOrg(orgCode)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该组织的财务数据");
        }
    }

    /** 薪资明文字段权限：独立于数据范围，默认脱敏 */
    public boolean canRevealSalary() {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        return user != null
                && (user.isSuperAdmin() || user.getPermissions() != null
                && user.getPermissions().contains(PermissionCodes.FINANCE_SALARY_VIEW));
    }

    /** 新建单据时归属组织：优先取账号数据范围根，其次取入参 */
    public String orgCodeOf(String requested) {
        String scope = currentScopeCode();
        return scope == null || scope.isBlank() ? requested : scope.trim();
    }
}
