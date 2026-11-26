package com.laimengchao.sso.contract.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.DataScope;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 合同模块数据权限服务：统一用 {@code org_code LIKE 'scopeCode%'} 过滤。
 * <p>
 * scopeCode 通过 {@link ScopeCodeCache} 拿（ThreadLocal → Redis → Feign 调 sso-account），
 * 支持逗号分隔多根；空范围一律 fail-closed 写 {@code __NO_ACCESS__}，返回空集。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService implements DataScope {

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
     * 把数据范围拼进任意业务表的 LambdaQueryWrapper。
     * 空 scopeCode（未配置）→ WHERE org_code = '__NO_ACCESS__'（安全兜底空集）。
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
            for (String raw : roots) {
                String root = raw == null ? "" : raw.trim();
                if (root.isEmpty()) {
                    continue;
                }
                if (first) {
                    w.likeRight(orgCodeFn, root);
                    first = false;
                } else {
                    w.or().likeRight(orgCodeFn, root);
                }
            }
            if (first) {
                w.eq(orgCodeFn, "__NO_ACCESS__");
            }
        });
    }

    /** 单条记录是否可见（返回 boolean，只读选项用） */
    public boolean canAccessOrg(String orgCode) {
        return DataScope.super.canAccessOrg(orgCode);
    }

    /** 单条记录是否可见（抛 Forbidden，写操作与详情用） */
    public void assertCanAccessOrg(String orgCode) {
        if (!canAccessOrg(orgCode)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该组织节点");
        }
    }

    /** 是否有指定权限码（敏感字段明文等独立按钮权限，与数据范围无关） */
    public boolean hasPermission(String code) {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        return user != null && user.getPermissions() != null && user.getPermissions().contains(code);
    }
}
