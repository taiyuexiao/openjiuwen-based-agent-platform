package com.bosc.agentops.delivery.executor;

import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.delivery.config.DeliveryProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 执行器路由：按 agentops.delivery.executor（local|openjiuwen）选择当前执行器；
 * stop 时按 deployment.executor 落库值路由回原执行器。
 */
@Component
public class DeploymentExecutorResolver {

    /** HOSTED（外部托管）部署不经过执行器，deployment.executor 落库为该值 */
    public static final String HOSTED_EXTERNAL = "HOSTED_EXTERNAL";

    private final DeliveryProperties properties;
    private final Map<String, DeploymentExecutor> executorsByName;

    public DeploymentExecutorResolver(DeliveryProperties properties, List<DeploymentExecutor> executors) {
        this.properties = properties;
        this.executorsByName = executors.stream()
                .collect(Collectors.toMap(DeploymentExecutor::name, Function.identity()));
    }

    /** 当前配置的执行器 */
    public DeploymentExecutor current() {
        String configured = properties.getExecutor();
        String executorName = switch (configured == null ? "local" : configured) {
            case "local" -> LocalStubExecutor.NAME;
            case "openjiuwen" -> OpenJiuwenRuntimeExecutor.NAME;
            default -> throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "未知的 agentops.delivery.executor 配置: " + configured + "（可选 local|openjiuwen）");
        };
        return byName(executorName);
    }

    public DeploymentExecutor byName(String name) {
        DeploymentExecutor executor = executorsByName.get(name);
        if (executor == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "执行器未装配: " + name + "（检查 agentops.delivery.executor 配置）");
        }
        return executor;
    }
}
