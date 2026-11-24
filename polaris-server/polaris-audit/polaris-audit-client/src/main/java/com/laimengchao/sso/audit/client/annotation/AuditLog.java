package com.laimengchao.sso.audit.client.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务方法审计增强（可选）。
 * <p>
 * 切面默认拦截所有 {@code @RestController} 的写方法（POST/PUT/PATCH/DELETE）自动留痕，
 * 无需逐方法标注——新接口天然被覆盖。本注解只用于补充语义：
 * <ul>
 *   <li>{@code action}：自定义动作码（默认取 类名.方法名）；</li>
 *   <li>{@code targetType}：对象类型（ACCOUNT/EMPLOYEE/FILE 等）；</li>
 *   <li>{@code ignore}：跳过审计（如纯查询导出外的内部调试端点）。</li>
 * </ul>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AuditLog {
    String action() default "";
    String targetType() default "";
    boolean ignore() default false;
}
