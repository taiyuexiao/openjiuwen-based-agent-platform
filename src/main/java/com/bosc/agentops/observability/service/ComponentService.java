package com.bosc.agentops.observability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.observability.dto.ComponentHealthItem;
import com.bosc.agentops.observability.dto.ComponentHealthSummary;
import com.bosc.agentops.observability.dto.ComponentListReq;
import com.bosc.agentops.observability.dto.ComponentRegisterReq;
import com.bosc.agentops.observability.dto.ComponentUpdateReq;
import com.bosc.agentops.observability.entity.ComponentRegistry;
import com.bosc.agentops.observability.entity.HealthStatus;
import com.bosc.agentops.observability.mapper.ComponentRegistryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * 组件运维：register/update/list + health 聚合。
 * health：对 healthEndpoint 逐个 GET（3 秒超时）——2xx → UP；非 2xx / 连接异常 → DOWN；
 * endpoint 未配置或不可解析 → UNKNOWN；汇总 total/up/down/unknown。
 */
@Service
public class ComponentService {

    static final int HEALTH_PROBE_TIMEOUT_MS = 3000;

    private final ComponentRegistryMapper componentMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public ComponentService(ComponentRegistryMapper componentMapper, AuditService auditService,
                            ObjectMapper objectMapper) {
        this.componentMapper = componentMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(HEALTH_PROBE_TIMEOUT_MS);
        factory.setReadTimeout(HEALTH_PROBE_TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Transactional
    public ComponentRegistry register(ComponentRegisterReq req) {
        ComponentRegistry component = new ComponentRegistry();
        component.setCode(req.getCode());
        component.setName(req.getName());
        component.setType(req.getType());
        component.setOwnerTeam(req.getOwnerTeam());
        component.setHealthEndpoint(req.getHealthEndpoint());
        component.setCritical(req.getCritical() != null && req.getCritical());
        component.setDescription(req.getDescription());
        component.setDeps(depsToJson(req.getDeps()));
        component.setCreatedBy(RequestContext.currentUserId());
        try {
            componentMapper.insert(component);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "组件 code 已存在: " + req.getCode());
        }
        auditService.record(AlertService.MODULE, "component.register", "component_registry",
                component.getId(), Map.of("code", component.getCode(), "type", component.getType().name()));
        return component;
    }

    @Transactional
    public ComponentRegistry update(ComponentUpdateReq req) {
        ComponentRegistry component = getOrThrow(req.getId());
        if (req.getName() != null) {
            component.setName(req.getName());
        }
        if (req.getOwnerTeam() != null) {
            component.setOwnerTeam(req.getOwnerTeam());
        }
        if (req.getHealthEndpoint() != null) {
            component.setHealthEndpoint(req.getHealthEndpoint());
        }
        if (req.getCritical() != null) {
            component.setCritical(req.getCritical());
        }
        if (req.getDescription() != null) {
            component.setDescription(req.getDescription());
        }
        if (req.getDeps() != null) {
            component.setDeps(depsToJson(req.getDeps()));
        }
        componentMapper.updateById(component);
        auditService.record(AlertService.MODULE, "component.update", "component_registry",
                component.getId(), Map.of("code", component.getCode()));
        return component;
    }

    public List<ComponentRegistry> list(ComponentListReq req) {
        return componentMapper.selectList(new LambdaQueryWrapper<ComponentRegistry>()
                .eq(req.getType() != null, ComponentRegistry::getType, req.getType())
                .eq(req.getCritical() != null, ComponentRegistry::getCritical, req.getCritical())
                .orderByAsc(ComponentRegistry::getId));
    }

    /** 健康聚合：逐个探测，单个组件失败不影响其他组件 */
    public ComponentHealthSummary health() {
        List<ComponentRegistry> components = componentMapper.selectList(
                new LambdaQueryWrapper<ComponentRegistry>().orderByAsc(ComponentRegistry::getId));
        ComponentHealthSummary summary = new ComponentHealthSummary();
        List<ComponentHealthItem> items = components.stream().map(this::probe).toList();
        summary.setComponents(items);
        summary.setTotal(items.size());
        summary.setUp((int) items.stream().filter(i -> i.getStatus() == HealthStatus.UP).count());
        summary.setDown((int) items.stream().filter(i -> i.getStatus() == HealthStatus.DOWN).count());
        summary.setUnknown((int) items.stream().filter(i -> i.getStatus() == HealthStatus.UNKNOWN).count());
        return summary;
    }

    public ComponentRegistry getOrThrow(Long id) {
        ComponentRegistry component = componentMapper.selectById(id);
        if (component == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "组件不存在: " + id);
        }
        return component;
    }

    private ComponentHealthItem probe(ComponentRegistry component) {
        ComponentHealthItem item = new ComponentHealthItem();
        item.setCode(component.getCode());
        item.setName(component.getName());
        item.setType(component.getType());
        item.setCritical(component.getCritical());
        item.setHealthEndpoint(component.getHealthEndpoint());
        if (component.getHealthEndpoint() == null || component.getHealthEndpoint().isBlank()) {
            item.setStatus(HealthStatus.UNKNOWN);
            item.setError("未配置 healthEndpoint");
            return item;
        }
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<Void> response = restClient.get()
                    .uri(component.getHealthEndpoint()).retrieve().toBodilessEntity();
            item.setHttpStatus(response.getStatusCode().value());
            item.setStatus(HealthStatus.UP);
            item.setLatencyMs(System.currentTimeMillis() - start);
        } catch (RestClientResponseException e) {
            item.setHttpStatus(e.getStatusCode().value());
            item.setStatus(HealthStatus.DOWN);
            item.setLatencyMs(System.currentTimeMillis() - start);
            item.setError("HTTP " + e.getStatusCode().value());
        } catch (IllegalArgumentException e) {
            item.setStatus(HealthStatus.UNKNOWN);
            item.setError("healthEndpoint 不可解析: " + e.getMessage());
        } catch (Exception e) {
            item.setStatus(HealthStatus.DOWN);
            item.setLatencyMs(System.currentTimeMillis() - start);
            item.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return item;
    }

    private String depsToJson(List<String> deps) {
        if (deps == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(deps);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "deps 序列化失败: " + e.getMessage());
        }
    }
}
