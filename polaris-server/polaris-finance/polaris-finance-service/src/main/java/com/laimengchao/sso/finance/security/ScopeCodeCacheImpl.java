package com.laimengchao.sso.finance.security;

import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 财务域 scopeCode 缓存实现：ThreadLocal（网关注入）→ 进程内短期缓存 → Feign 调 sso-account。
 * 数据所有者是 sso-account，财务服务不查 sso_account 表。
 * <p>
 * 财务服务未引入 Redis starter，缓存走进程内 ConcurrentHashMap + 短 TTL；
 * 缓存/Feign 失败只 WARN 并按空范围返回（fail-closed），不阻断业务查询。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScopeCodeCacheImpl implements ScopeCodeCache {

    private final AccountApi accountApi;
    private final Map<Long, CacheEntry> localCache = new ConcurrentHashMap<>();

    @Override
    public String get(Long accountId) {
        if (accountId == null) {
            return null;
        }
        AccountContext.AccountInfo info = AccountContext.get();
        if (info != null && info.getDataScopeOrgCode() != null) {
            return info.getDataScopeOrgCode();
        }
        CacheEntry cached = localCache.get(accountId);
        if (cached != null && !cached.expired()) {
            return cached.value;
        }
        String fresh = null;
        try {
            R<String> resp = accountApi.dataScopeCode(accountId);
            fresh = resp == null || !resp.ok() ? null : resp.getData();
        } catch (Exception e) {
            log.warn("财务查询账号中心数据范围失败，按空范围处理，accountId={}，原因={}",
                    accountId, e.getMessage());
        }
        put(accountId, fresh);
        return fresh;
    }

    @Override
    public void put(Long accountId, String scopeCode) {
        if (accountId == null) {
            return;
        }
        if (scopeCode == null) {
            localCache.remove(accountId);
        } else {
            localCache.put(accountId, new CacheEntry(scopeCode));
        }
    }

    @Override
    public void invalidate(Long accountId) {
        if (accountId != null) {
            localCache.remove(accountId);
        }
    }

    private static final class CacheEntry {
        private final String value;
        private final long expireAt = System.currentTimeMillis() + DEFAULT_TTL_MINUTES * 60_000L;

        private CacheEntry(String value) {
            this.value = value;
        }

        private boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
