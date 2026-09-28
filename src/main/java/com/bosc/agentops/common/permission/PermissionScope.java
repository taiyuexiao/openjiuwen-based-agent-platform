package com.bosc.agentops.common.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式指定权限 scope 的 SpEL 表达式（以方法参数为上下文），
 * 用于参数名不是 projectId 的场景，如 @PermissionScope("#req.id")。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface PermissionScope {

    String value();
}
