package com.bosc.agentops.common.permission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限点显式注册 + 默认拒绝：启动时扫描 /v1/api/** 接口，
 * 未标注 @RequirePermission 的写接口启动即告警（后续可改为拒绝启动）。
 * 机器通道（OTLP 接收、告警 webhook）不走管理面权限注解，经
 * agentops.permission.audit-exclude-prefixes 配置排除后不再告警（由配置 token 校验）。
 */
@Component
public class PermissionRegistrationAuditor implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionRegistrationAuditor.class);

    private final RequestMappingHandlerMapping handlerMapping;
    private final List<String> excludePrefixes;

    public PermissionRegistrationAuditor(RequestMappingHandlerMapping handlerMapping,
                                         @Value("${agentops.permission.audit-exclude-prefixes:}")
                                         List<String> excludePrefixes) {
        this.handlerMapping = handlerMapping;
        this.excludePrefixes = excludePrefixes == null ? List.of() : excludePrefixes;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Map.Entry<?, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod method = entry.getValue();
            Class<?> beanType = method.getBeanType();
            if (!beanType.getName().startsWith("com.bosc.agentops")) {
                continue;
            }
            if (entry.getKey() instanceof RequestMappingInfo info && isExcluded(info)) {
                continue;
            }
            if (!method.hasMethodAnnotation(RequirePermission.class)) {
                log.warn("接口未注册权限点（默认拒绝策略下视为越权面）: {}.{}",
                        beanType.getSimpleName(), method.getMethod().getName());
            }
        }
    }

    private boolean isExcluded(RequestMappingInfo info) {
        if (excludePrefixes.isEmpty()) {
            return false;
        }
        Set<String> patterns = info.getPathPatternsCondition() != null
                ? info.getPathPatternsCondition().getPatternValues()
                : (info.getPatternsCondition() == null ? Set.of() : info.getPatternsCondition().getPatterns());
        for (String pattern : patterns) {
            for (String prefix : excludePrefixes) {
                if (!prefix.isBlank() && pattern.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }
}
