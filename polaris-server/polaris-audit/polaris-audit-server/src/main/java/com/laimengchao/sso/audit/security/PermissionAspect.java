package com.laimengchao.sso.audit.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/** 管理端权限切面：未登录 401 → 令牌应用域校验 → 超管旁路 → 权限码判定。 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    @Before("@annotation(com.laimengchao.sso.audit.security.Permit)")
    public void check(JoinPoint joinPoint) {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Permit permit = method.getAnnotation(Permit.class);
        String[] apps = permit.app() == null || permit.app().length == 0
                ? new String[]{PermissionCodes.APP_AUDIT} : permit.app();
        if (Arrays.stream(apps).noneMatch(app -> app != null && app.equals(user.getAppCode()))) {
            log.warn("跨应用访问审计管理接口被拒：app={} 允许={} 账户={}", user.getAppCode(),
                    Arrays.toString(apps), user.getUsername());
            throw new BizException(ErrorCode.FORBIDDEN,
                    "该接口仅限指定应用令牌访问（当前作用域：" + user.getAppCode() + "）");
        }
        if (user.isSuperAdmin()) {
            return;
        }
        Set<String> owned = user.getPermissions() == null ? Set.of() : user.getPermissions();
        boolean ok = switch (permit.logical()) {
            case ALL -> Arrays.stream(permit.value()).allMatch(owned::contains);
            case ANY -> Arrays.stream(permit.value()).anyMatch(owned::contains);
        };
        if (!ok) {
            log.warn("权限不足：账户={} 需要={} 拥有={}", user.getUsername(),
                    Arrays.toString(permit.value()), owned);
            throw new BizException(ErrorCode.FORBIDDEN, "无权限：" + String.join("/", permit.value()));
        }
    }
}
