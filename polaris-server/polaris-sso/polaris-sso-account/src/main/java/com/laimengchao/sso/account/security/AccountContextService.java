package com.laimengchao.sso.account.security;


import java.util.Set;





public interface AccountContextService {

    /**
     * 直接采用网关验签后的结论填充上下文：不查库，零额外开销。
     * 权限码来自 token 快照，授权回收后要等 token 过期（最长 access TTL）才生效——
     * 需要「立即失权」时把这里换成按 accountId 查库并加缓存。
     */
    void fillFromHeaders(Long accountId, String username, boolean superAdmin, Set<String> permissions);

    /**
     * @param appCode 令牌所属应用（来自网关的 X-App-Code）；null 视为平台自身
     */
    void fillFromHeaders(Long accountId, String username, boolean superAdmin, Set<String> permissions, String appCode);

    void fill(Long accountId);
}
