package com.bosc.agentops.modelknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.modelknowledge.dto.EffectiveModel;
import com.bosc.agentops.modelknowledge.entity.CatalogStatus;
import com.bosc.agentops.modelknowledge.entity.ModelProvider;
import com.bosc.agentops.modelknowledge.entity.ModelService;
import com.bosc.agentops.modelknowledge.entity.ProjectModelGrant;
import com.bosc.agentops.modelknowledge.mapper.ModelProviderMapper;
import com.bosc.agentops.modelknowledge.mapper.ModelServiceMapper;
import com.bosc.agentops.modelknowledge.mapper.ProjectModelGrantMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ModelAccessService 实现。
 * 参数策略合并规则：effective = 模型默认参数 ← 项目 param_policy 允许范围内的覆盖；
 * policy 非空时，policy 外的默认参数键直接剔除并在 EffectiveModel.removedKeys 中标注；
 * policy 条目的 value 为覆盖值，min/max 为数值上下限（越界收敛到边界）。
 */
@Service
public class ModelAccessServiceImpl implements ModelAccessService {

    private final ProjectModelGrantMapper projectModelGrantMapper;
    private final ModelServiceMapper modelServiceMapper;
    private final ModelProviderMapper modelProviderMapper;
    private final ObjectMapper objectMapper;

    public ModelAccessServiceImpl(ProjectModelGrantMapper projectModelGrantMapper,
                                  ModelServiceMapper modelServiceMapper,
                                  ModelProviderMapper modelProviderMapper,
                                  ObjectMapper objectMapper) {
        this.projectModelGrantMapper = projectModelGrantMapper;
        this.modelServiceMapper = modelServiceMapper;
        this.modelProviderMapper = modelProviderMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<EffectiveModel> effectiveModels(Long projectId) {
        List<ProjectModelGrant> grants = projectModelGrantMapper.selectList(
                new LambdaQueryWrapper<ProjectModelGrant>()
                        .eq(ProjectModelGrant::getProjectId, projectId)
                        .orderByAsc(ProjectModelGrant::getId));
        List<EffectiveModel> result = new ArrayList<>();
        for (ProjectModelGrant grant : grants) {
            ModelService modelService = modelServiceMapper.selectById(grant.getModelServiceId());
            // disable 语义：目录项停用后不再下发，历史授权记录保留
            if (modelService == null || modelService.getStatus() != CatalogStatus.ENABLED) {
                continue;
            }
            ModelProvider provider = modelProviderMapper.selectById(modelService.getProviderId());
            if (provider == null || provider.getStatus() != CatalogStatus.ENABLED) {
                continue;
            }
            Map<String, Object> defaults = JsonSupport.toMap(objectMapper, modelService.getDefaultParams());
            Map<String, Object> policy = JsonSupport.toMap(objectMapper, grant.getParamPolicy());
            ParamMerge merge = mergeParams(defaults, policy);

            EffectiveModel effective = new EffectiveModel();
            effective.setGrantId(grant.getId());
            effective.setModelServiceId(modelService.getId());
            effective.setModelCode(modelService.getModelCode());
            effective.setDisplayName(modelService.getDisplayName());
            effective.setProviderId(provider.getId());
            effective.setProviderCode(provider.getCode());
            effective.setProviderType(provider.getProviderType().name());
            effective.setEndpoint(provider.getEndpoint());
            effective.setAuthType(provider.getAuthType().name());
            effective.setCredentialRef(provider.getCredentialRef());
            effective.setCapabilities(JsonSupport.toNode(objectMapper, modelService.getCapabilities()));
            effective.setEffectiveParams(merge.effectiveParams());
            effective.setRemovedKeys(merge.removedKeys());
            result.add(effective);
        }
        return result;
    }

    @Override
    public boolean checkModelAllowed(Long projectId, String modelCode) {
        if (projectId == null || modelCode == null) {
            return false;
        }
        return effectiveModels(projectId).stream()
                .anyMatch(m -> m.getModelCode().equals(modelCode));
    }

    @Override
    public List<String> removedParamKeys(Long projectId, String modelCode, Map<String, Object> declaredParams) {
        Map<String, Object> declared = declaredParams == null ? Map.of() : declaredParams;
        if (projectId == null || modelCode == null) {
            return List.copyOf(declared.keySet());
        }
        List<ProjectModelGrant> grants = projectModelGrantMapper.selectList(
                new LambdaQueryWrapper<ProjectModelGrant>()
                        .eq(ProjectModelGrant::getProjectId, projectId)
                        .orderByAsc(ProjectModelGrant::getId));
        for (ProjectModelGrant grant : grants) {
            ModelService modelService = modelServiceMapper.selectById(grant.getModelServiceId());
            if (modelService == null || modelService.getStatus() != CatalogStatus.ENABLED
                    || !modelCode.equals(modelService.getModelCode())) {
                continue;
            }
            ModelProvider provider = modelProviderMapper.selectById(modelService.getProviderId());
            if (provider == null || provider.getStatus() != CatalogStatus.ENABLED) {
                continue;
            }
            // 复用参数合并逻辑：声明参数作输入、param_policy 作白名单，policy 外的声明键被剔除
            Map<String, Object> policy = JsonSupport.toMap(objectMapper, grant.getParamPolicy());
            return mergeParams(declared, policy).removedKeys();
        }
        return List.copyOf(declared.keySet());
    }

    /**
     * 参数合并：policy 为空 → 默认参数全量放行；
     * policy 非空 → 默认参数仅保留 policy 白名单内的键（其余进 removedKeys），
     * 再应用 policy 内带 value 的覆盖（数值按 min/max 收敛到边界）。
     */
    static ParamMerge mergeParams(Map<String, Object> defaults, Map<String, Object> policy) {
        Map<String, Object> effective = new LinkedHashMap<>();
        List<String> removedKeys = new ArrayList<>();
        if (policy == null || policy.isEmpty()) {
            effective.putAll(defaults);
            return new ParamMerge(effective, removedKeys);
        }
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (policy.containsKey(entry.getKey())) {
                effective.put(entry.getKey(), entry.getValue());
            } else {
                removedKeys.add(entry.getKey());
            }
        }
        for (Map.Entry<String, Object> entry : policy.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> rule) || !rule.containsKey("value")) {
                continue;
            }
            Object value = rule.get("value");
            if (value instanceof Number number) {
                double v = number.doubleValue();
                if (rule.get("max") instanceof Number max && v > max.doubleValue()) {
                    value = max;
                } else if (rule.get("min") instanceof Number min && v < min.doubleValue()) {
                    value = min;
                }
            }
            effective.put(entry.getKey(), value);
        }
        return new ParamMerge(effective, removedKeys);
    }

    record ParamMerge(Map<String, Object> effectiveParams, List<String> removedKeys) {
    }
}
