package com.bosc.agentops.common.permission;

import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.context.RequestContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * @RequirePermission AOP 拦截器。
 * scope 解析规则（按序）：
 * 1. 参数上的 @PermissionScope SpEL；
 * 2. 名为 projectId 的参数；
 * 3. 参数对象的 projectId 属性（getProjectId）。
 */
@Aspect
@Component
public class PermissionAspect {

    private final PermissionService permissionService;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final ExpressionParser spelParser = new SpelExpressionParser();

    public PermissionAspect(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Around("@annotation(requirePermission)")
    public Object around(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        String userId = RequestContext.currentUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Long projectId = null;
        if (requirePermission.projectScoped()) {
            projectId = resolveProjectId(joinPoint);
            if (projectId == null) {
                throw new BizException(ErrorCode.INTERNAL_ERROR,
                        "无法从方法参数解析权限 scope(projectId): " + requirePermission.value());
            }
        }
        boolean allowed = permissionService.check(userId, requirePermission.value(), projectId);
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    ErrorCode.FORBIDDEN.getDefaultMessage() + ": " + requirePermission.value());
        }
        return joinPoint.proceed();
    }

    private Long resolveProjectId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);

        for (int i = 0; i < parameters.length; i++) {
            PermissionScope scope = parameters[i].getAnnotation(PermissionScope.class);
            if (scope != null) {
                StandardEvaluationContext context = new StandardEvaluationContext();
                for (int j = 0; j < args.length; j++) {
                    if (paramNames != null && paramNames[j] != null) {
                        context.setVariable(paramNames[j], args[j]);
                    }
                }
                Object value = spelParser.parseExpression(scope.value()).getValue(context);
                return toLong(value);
            }
        }
        for (int i = 0; i < parameters.length; i++) {
            if ("projectId".equals(parameters[i].getName())) {
                return toLong(args[i]);
            }
        }
        for (Object arg : args) {
            Long value = readProjectIdProperty(arg);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long readProjectIdProperty(Object arg) {
        if (arg == null || arg.getClass().getName().startsWith("java.")) {
            return null;
        }
        try {
            Method getter = arg.getClass().getMethod("getProjectId");
            return toLong(getter.invoke(arg));
        } catch (NoSuchMethodException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "权限 scope 解析失败: " + e.getMessage());
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
