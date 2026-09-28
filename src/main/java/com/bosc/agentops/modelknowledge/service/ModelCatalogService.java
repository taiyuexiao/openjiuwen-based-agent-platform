package com.bosc.agentops.modelknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.modelknowledge.dto.ModelProviderCreateReq;
import com.bosc.agentops.modelknowledge.dto.ModelProviderUpdateReq;
import com.bosc.agentops.modelknowledge.dto.ModelServiceCreateReq;
import com.bosc.agentops.modelknowledge.dto.ModelServiceUpdateReq;
import com.bosc.agentops.modelknowledge.entity.CatalogStatus;
import com.bosc.agentops.modelknowledge.entity.ModelProvider;
import com.bosc.agentops.modelknowledge.entity.ModelService;
import com.bosc.agentops.modelknowledge.mapper.ModelProviderMapper;
import com.bosc.agentops.modelknowledge.mapper.ModelServiceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 模型目录管理：Provider 与 ModelService 的建/改/停/查。
 * 平台级目录操作本期约定为「认证用户 + 记录操作人」（与 project:create 同级），后续接平台运营角色收紧。
 */
@Service
public class ModelCatalogService {

    static final String MODULE = "model-knowledge";

    private final ModelProviderMapper modelProviderMapper;
    private final ModelServiceMapper modelServiceMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ModelCatalogService(ModelProviderMapper modelProviderMapper,
                               ModelServiceMapper modelServiceMapper,
                               AuditService auditService,
                               ObjectMapper objectMapper) {
        this.modelProviderMapper = modelProviderMapper;
        this.modelServiceMapper = modelServiceMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ModelProvider createProvider(ModelProviderCreateReq req) {
        String userId = RequestContext.currentUserId();
        ModelProvider provider = new ModelProvider();
        provider.setCode(req.getCode());
        provider.setName(req.getName());
        provider.setProviderType(req.getProviderType());
        provider.setEndpoint(req.getEndpoint());
        provider.setAuthType(req.getAuthType());
        provider.setCredentialRef(req.getCredentialRef());
        provider.setStatus(CatalogStatus.ENABLED);
        provider.setCreatedBy(userId);
        try {
            modelProviderMapper.insert(provider);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "Provider code 已存在: " + req.getCode());
        }
        auditService.record(MODULE, "provider.create", "model_provider", provider.getId(),
                Map.of("code", provider.getCode(), "name", provider.getName()));
        return provider;
    }

    @Transactional
    public ModelProvider updateProvider(ModelProviderUpdateReq req) {
        ModelProvider provider = getProviderOrThrow(req.getId());
        provider.setName(req.getName());
        provider.setEndpoint(req.getEndpoint());
        provider.setAuthType(req.getAuthType());
        provider.setCredentialRef(req.getCredentialRef());
        modelProviderMapper.updateById(provider);
        auditService.record(MODULE, "provider.update", "model_provider", provider.getId(),
                Map.of("code", provider.getCode(), "name", provider.getName()));
        return provider;
    }

    @Transactional
    public ModelProvider disableProvider(Long id) {
        ModelProvider provider = getProviderOrThrow(id);
        if (provider.getStatus() == CatalogStatus.DISABLED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "Provider 已是停用状态: " + id);
        }
        provider.setStatus(CatalogStatus.DISABLED);
        modelProviderMapper.updateById(provider);
        // disable 语义：目录项停用后 effectiveModels 不再下发其模型，历史授权记录保留
        auditService.record(MODULE, "provider.disable", "model_provider", provider.getId(),
                Map.of("code", provider.getCode()));
        return provider;
    }

    public List<ModelProvider> listProviders() {
        return modelProviderMapper.selectList(new LambdaQueryWrapper<ModelProvider>()
                .orderByAsc(ModelProvider::getId));
    }

    @Transactional
    public ModelService createModelService(ModelServiceCreateReq req) {
        String userId = RequestContext.currentUserId();
        getProviderOrThrow(req.getProviderId());
        ModelService modelService = new ModelService();
        modelService.setProviderId(req.getProviderId());
        modelService.setModelCode(req.getModelCode());
        modelService.setDisplayName(req.getDisplayName());
        modelService.setCapabilities(JsonSupport.toJson(objectMapper, req.getCapabilities()));
        modelService.setDefaultParams(JsonSupport.toJson(objectMapper, req.getDefaultParams()));
        modelService.setStatus(CatalogStatus.ENABLED);
        modelService.setCreatedBy(userId);
        try {
            modelServiceMapper.insert(modelService);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "该 Provider 下模型已存在: " + req.getModelCode());
        }
        auditService.record(MODULE, "model.create", "model_service", modelService.getId(),
                Map.of("providerId", req.getProviderId(), "modelCode", req.getModelCode()));
        return modelService;
    }

    @Transactional
    public ModelService updateModelService(ModelServiceUpdateReq req) {
        ModelService modelService = getModelServiceOrThrow(req.getId());
        modelService.setDisplayName(req.getDisplayName());
        if (req.getCapabilities() != null) {
            modelService.setCapabilities(JsonSupport.toJson(objectMapper, req.getCapabilities()));
        }
        if (req.getDefaultParams() != null) {
            modelService.setDefaultParams(JsonSupport.toJson(objectMapper, req.getDefaultParams()));
        }
        modelServiceMapper.updateById(modelService);
        auditService.record(MODULE, "model.update", "model_service", modelService.getId(),
                Map.of("modelCode", modelService.getModelCode()));
        return modelService;
    }

    @Transactional
    public ModelService disableModelService(Long id) {
        ModelService modelService = getModelServiceOrThrow(id);
        if (modelService.getStatus() == CatalogStatus.DISABLED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "模型已是停用状态: " + id);
        }
        modelService.setStatus(CatalogStatus.DISABLED);
        modelServiceMapper.updateById(modelService);
        // disable 语义：停用后 effectiveModels 不再返回该模型，历史授权记录保留
        auditService.record(MODULE, "model.disable", "model_service", modelService.getId(),
                Map.of("modelCode", modelService.getModelCode()));
        return modelService;
    }

    public List<ModelService> listModelServices(Long providerId) {
        LambdaQueryWrapper<ModelService> wrapper = new LambdaQueryWrapper<ModelService>()
                .eq(providerId != null, ModelService::getProviderId, providerId)
                .orderByAsc(ModelService::getId);
        return modelServiceMapper.selectList(wrapper);
    }

    public ModelProvider getProviderOrThrow(Long id) {
        ModelProvider provider = modelProviderMapper.selectById(id);
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Provider 不存在: " + id);
        }
        return provider;
    }

    public ModelService getModelServiceOrThrow(Long id) {
        ModelService modelService = modelServiceMapper.selectById(id);
        if (modelService == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型服务不存在: " + id);
        }
        return modelService;
    }
}
