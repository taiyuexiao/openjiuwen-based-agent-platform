package com.bosc.agentops.delivery.executor;

import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.delivery.config.DeliveryProperties;
import com.bosc.agentops.delivery.entity.DeployTarget;
import com.bosc.agentops.delivery.entity.HealthStatus;
import com.bosc.agentops.delivery.entity.Release;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * openJiuwen agent-runtime 执行器骨架：HTTP 调 agent-runtime 的 /api/v1/agents/deploy。
 * 未做生产验证前标注为未启用：仅当 agentops.delivery.executor=openjiuwen 时装配，
 * 且 agentops.delivery.openjiuwen.runtime-base-url 未配置时构造即报错（明确失败而非静默回退）。
 */
@Component
@ConditionalOnProperty(prefix = "agentops.delivery", name = "executor", havingValue = "openjiuwen")
public class OpenJiuwenRuntimeExecutor implements DeploymentExecutor {

    public static final String NAME = "OPENJIUWEN_RUNTIME";

    private final String runtimeBaseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenJiuwenRuntimeExecutor(DeliveryProperties properties, ObjectMapper objectMapper) {
        String baseUrl = properties.getOpenjiuwen().getRuntimeBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "agentops.delivery.executor=openjiuwen 但未配置 agentops.delivery.openjiuwen.runtime-base-url");
        }
        this.runtimeBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public DeploymentResult deploy(Release release, DeployTarget target, DeployPlan plan) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("agentId", release.getAgentId());
        body.put("agentVersion", release.getAgentVersion());
        body.put("releaseId", release.getId());
        body.put("deploymentId", plan.getDeploymentId());
        body.put("cluster", target.getCluster());
        body.put("namespace", target.getNamespace());
        body.put("replicas", plan.getReplicas());
        body.put("level", plan.getLevel());
        ObjectNode resources = body.putObject("resources");
        resources.put("cpu", plan.getCpu());
        resources.put("memory", plan.getMemory());
        ObjectNode healthCheck = body.putObject("healthCheck");
        healthCheck.put("path", plan.getHealthCheckPath());
        healthCheck.put("intervalSeconds", plan.getHealthCheckIntervalSeconds());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(runtimeBaseUrl + "/api/v1/agents/deploy"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return DeploymentResult.failure("agent-runtime 部署失败: HTTP " + response.statusCode());
            }
            JsonNode respBody = objectMapper.readTree(response.body());
            JsonNode instanceUrl = respBody.get("instanceUrl");
            return DeploymentResult.success(instanceUrl == null || instanceUrl.isNull()
                    ? runtimeBaseUrl + "/instances/" + plan.getDeploymentId() : instanceUrl.asText());
        } catch (IOException e) {
            return DeploymentResult.failure("agent-runtime 调用失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DeploymentResult.failure("agent-runtime 调用被中断");
        }
    }

    @Override
    public void stop(Long deploymentId) {
        postQuietly("/api/v1/agents/deployments/" + deploymentId + "/stop");
    }

    @Override
    public HealthStatus healthCheck(Long deploymentId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(runtimeBaseUrl + "/api/v1/agents/deployments/" + deploymentId + "/health"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return HealthStatus.UNHEALTHY;
            }
            JsonNode body = objectMapper.readTree(response.body());
            JsonNode status = body.get("status");
            return status != null && "UP".equalsIgnoreCase(status.asText())
                    ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY;
        } catch (IOException e) {
            return HealthStatus.UNHEALTHY;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HealthStatus.UNHEALTHY;
        }
    }

    private void postQuietly(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(runtimeBaseUrl + path))
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "agent-runtime 调用失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "agent-runtime 调用被中断");
        }
    }
}
