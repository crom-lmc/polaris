package com.laimengchao.sso.openapi.security;

import java.util.Set;

/**
 * 开放接口权限判定：令牌 perms 为已授权接口编码（授权表一行一个接口）。
 * <p>
 * 新版机器令牌发 api_code，停用某接口后其编码不再进入令牌，
 * 因此该接口调用会被拒绝，而共用同一权限码的其他接口不受影响。
 * 兼容旧令牌仍以权限码（perm_code）授权的情况，两种任一命中即放行。
 */
public final class ApiPermissionChecker {

    private ApiPermissionChecker() {
    }

    /** 通配权限直接放行；否则 api_code 或 perm_code 命中其一即可。 */
    public static boolean hasPermission(String apiCode, String permCode, Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        if (permissions.contains("*")) {
            return true;
        }
        if (apiCode != null && !apiCode.isBlank() && permissions.contains(apiCode)) {
            return true;
        }
        return permCode != null && !permCode.isBlank() && permissions.contains(permCode);
    }
}
