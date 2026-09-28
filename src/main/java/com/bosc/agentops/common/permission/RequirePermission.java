package com.bosc.agentops.common.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限点注解。标注在 Controller 方法上，由 PermissionAspect 拦截判定。
 * projectScoped=true 时从方法参数解析 projectId（见 PermissionAspect 解析规则）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** 权限点，如 project:member:manage */
    String value();

    /** true=项目级权限，需要解析 projectId；false=平台级权限 */
    boolean projectScoped() default true;
}
