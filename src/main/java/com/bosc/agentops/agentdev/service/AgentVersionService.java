package com.bosc.agentops.agentdev.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.dto.VersionDetailReq;
import com.bosc.agentops.agentdev.dto.VersionRegisterReq;
import com.bosc.agentops.agentdev.entity.AccessMode;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.entity.AgentStatus;
import com.bosc.agentops.agentdev.entity.AgentVersion;
import com.bosc.agentops.agentdev.entity.AgentVersionStatus;
import com.bosc.agentops.agentdev.mapper.AgentVersionMapper;
import com.bosc.agentops.assethub.dto.ReferenceAddReq;
import com.bosc.agentops.assethub.service.AssetAccessService;
import com.bosc.agentops.assethub.service.AssetReferenceService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.modelknowledge.dto.KbRefBindReq;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBaseRef;
import com.bosc.agentops.modelknowledge.service.KnowledgeAccessService;
import com.bosc.agentops.modelknowledge.service.KnowledgeBaseRefService;
import com.bosc.agentops.modelknowledge.service.ModelAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 版本登记与查询。登记即「声明登记校验」：
 * model（授权 + 参数策略合并）/ skills / mcpTools / knowledgeBases 逐项校验，
 * 全部失败项收集后一次性返回（40910）；全部通过后写 AssetReference / KnowledgeBaseRef（幂等）
 * 并置 REGISTERED（declaration 自此不可变，同 agent 版本号唯一）。
 */
@Service
public class AgentVersionService {

    private final AgentVersionMapper agentVersionMapper;
    private final AgentService agentService;
    private final ModelAccessService modelAccessService;
    private final AssetAccessService assetAccessService;
    private final AssetReferenceService assetReferenceService;
    private final KnowledgeAccessService knowledgeAccessService;
    private final KnowledgeBaseRefService knowledgeBaseRefService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AgentVersionService(AgentVersionMapper agentVersionMapper,
                               AgentService agentService,
                               ModelAccessService modelAccessService,
                               AssetAccessService assetAccessService,
                               AssetReferenceService assetReferenceService,
                               KnowledgeAccessService knowledgeAccessService,
                               KnowledgeBaseRefService knowledgeBaseRefService,
                               AuditService auditService,
                               ObjectMapper objectMapper) {
        this.agentVersionMapper = agentVersionMapper;
        this.agentService = agentService;
        this.modelAccessService = modelAccessService;
        this.assetAccessService = assetAccessService;
        this.assetReferenceService = assetReferenceService;
        this.knowledgeAccessService = knowledgeAccessService;
        this.knowledgeBaseRefService = knowledgeBaseRefService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentVersion register(Long agentId, VersionRegisterReq req) {
        String userId = RequestContext.currentUserId();
        Agent agent = agentService.getOrThrow(agentId);
        agentService.requireOwnerProject(agent, req.getProjectId());
        if (agent.getStatus() == AgentStatus.ARCHIVED) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "Agent 已归档，禁止登记新版本: " + agentId);
        }
        if (agent.getAccessMode() == AccessMode.HOSTED) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "托管模式（HOSTED）无需登记版本：平台仅承诺服务调用、生命周期与基础日志，"
                            + "运行入口与健康检查地址已在 Agent 上记录: " + agentId);
        }
        // 版本不可变：同 agent 下版本号唯一，重复登记即拒绝（不允许改写已登记声明）
        AgentVersion existing = findVersion(agentId, req.getVersion());
        if (existing != null) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "版本已登记且 declaration 不可变，请使用新版本号: " + req.getVersion());
        }

        boolean emptyDeclaration = isEmptyDeclaration(req.getDeclaration());
        boolean capabilityDegraded = false;
        if (emptyDeclaration) {
            if (agent.getAccessMode() == AccessMode.NATIVE) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "NATIVE 模式登记版本必须提供完整声明清单（declaration）");
            }
            // ADAPTED 空声明：允许登记，标记能力降级
            capabilityDegraded = true;
        } else {
            List<String> failures = validateDeclaration(agent, req.getDeclaration());
            if (!failures.isEmpty()) {
                throw new BizException(ErrorCode.DECLARATION_INVALID,
                        ErrorCode.DECLARATION_INVALID.getDefaultMessage() + ": "
                                + String.join("；", failures));
            }
        }

        AgentVersion version = new AgentVersion();
        version.setAgentId(agentId);
        version.setVersion(req.getVersion());
        version.setDeclaration(JsonSupport.toJson(objectMapper, req.getDeclaration()));
        version.setCapabilityDegraded(capabilityDegraded);
        version.setStatus(AgentVersionStatus.REGISTERED);
        version.setRegisteredBy(userId);
        version.setRegisteredAt(LocalDateTime.now());
        version.setCreatedBy(userId);
        try {
            agentVersionMapper.insert(version);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "版本已登记且 declaration 不可变，请使用新版本号: " + req.getVersion());
        }
        // 校验通过后登记依赖引用（幂等），完成「平台识别并管理声明」
        if (!emptyDeclaration) {
            writeReferences(agent, req.getDeclaration());
        }
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("agentId", agentId);
        auditDetail.put("version", version.getVersion());
        auditDetail.put("capabilityDegraded", capabilityDegraded);
        auditService.record(AgentService.MODULE, "agent.version.register", "agent_version",
                version.getId(), auditDetail);
        return version;
    }

    public AgentVersion detail(Long agentId, VersionDetailReq req) {
        Agent agent = agentService.getOrThrow(agentId);
        agentService.requireOwnerProject(agent, req.getProjectId());
        AgentVersion version = findVersion(agentId, req.getVersion());
        if (version == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "Agent 版本不存在: agentId=" + agentId + ", version=" + req.getVersion());
        }
        return version;
    }

    public List<AgentVersion> list(Long agentId, Long projectId) {
        Agent agent = agentService.getOrThrow(agentId);
        agentService.requireOwnerProject(agent, projectId);
        return agentVersionMapper.selectList(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentId, agentId)
                .orderByAsc(AgentVersion::getId));
    }

    private AgentVersion findVersion(Long agentId, String version) {
        return agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersion>()
                .eq(AgentVersion::getAgentId, agentId)
                .eq(AgentVersion::getVersion, version));
    }

    private static boolean isEmptyDeclaration(JsonNode declaration) {
        return declaration == null || declaration.isNull()
                || (declaration.isObject() && declaration.isEmpty());
    }

    /**
     * 声明登记校验：逐项校验并收集全部失败项（不短路）。
     * model 必填校验只针对出现的声明项；prompt/memory 为自由结构，本期不做授权校验。
     */
    private List<String> validateDeclaration(Agent agent, JsonNode declaration) {
        if (!declaration.isObject()) {
            return List.of("declaration 必须是 JSON 对象");
        }
        List<String> failures = new ArrayList<>();
        Long projectId = agent.getProjectId();

        JsonNode model = declaration.get("model");
        if (model != null && !model.isNull()) {
            JsonNode modelCode = model.get("modelCode");
            if (modelCode == null || !modelCode.isTextual() || modelCode.asText().isBlank()) {
                failures.add("model.modelCode 缺失或为空");
            } else {
                String code = modelCode.asText();
                if (!modelAccessService.checkModelAllowed(projectId, code)) {
                    failures.add("模型未授权或目录项已停用: modelCode=" + code);
                } else {
                    Map<String, Object> declaredParams = new LinkedHashMap<>();
                    JsonNode params = model.get("params");
                    if (params != null && params.isObject()) {
                        params.fields().forEachRemaining(
                                e -> declaredParams.put(e.getKey(), objectMapper.convertValue(e.getValue(), Object.class)));
                    }
                    List<String> removedKeys = modelAccessService.removedParamKeys(projectId, code, declaredParams);
                    if (!removedKeys.isEmpty()) {
                        failures.add("模型参数越出项目参数策略: modelCode=" + code + ", 被剔除参数=" + removedKeys);
                    }
                }
            }
        }

        collectAssetFailures(declaration.get("skills"), "Skill", projectId, failures);
        collectAssetFailures(declaration.get("mcpTools"), "MCP 工具", projectId, failures);

        JsonNode knowledgeBases = declaration.get("knowledgeBases");
        if (knowledgeBases != null && !knowledgeBases.isNull()) {
            if (!knowledgeBases.isArray()) {
                failures.add("knowledgeBases 必须是数组");
            } else {
                for (int i = 0; i < knowledgeBases.size(); i++) {
                    JsonNode item = knowledgeBases.get(i);
                    JsonNode kbId = item == null ? null : item.get("kbId");
                    if (kbId == null || !kbId.canConvertToLong()) {
                        failures.add("knowledgeBases[" + i + "].kbId 缺失");
                        continue;
                    }
                    if (!knowledgeAccessService.checkKbAllowed(projectId, agent.getId(), kbId.asLong())) {
                        failures.add("知识库未授权或无引用绑定: kbId=" + kbId.asLong());
                    }
                }
            }
        }
        return failures;
    }

    private void collectAssetFailures(JsonNode items, String label, Long projectId, List<String> failures) {
        if (items == null || items.isNull()) {
            return;
        }
        String field = label.equals("Skill") ? "skills" : "mcpTools";
        if (!items.isArray()) {
            failures.add(field + " 必须是数组");
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            JsonNode assetId = item == null ? null : item.get("assetId");
            JsonNode assetVersion = item == null ? null : item.get("version");
            if (assetId == null || !assetId.canConvertToLong()
                    || assetVersion == null || !assetVersion.isTextual() || assetVersion.asText().isBlank()) {
                failures.add(field + "[" + i + "] 缺少 assetId 或 version");
                continue;
            }
            if (!assetAccessService.checkUsable(assetId.asLong(), assetVersion.asText(), projectId)) {
                failures.add(label + "资产不可用（未发布/未授权/已下线）: assetId=" + assetId.asLong()
                        + ", version=" + assetVersion.asText());
            }
        }
    }

    /** 校验通过后登记依赖引用：AssetReference / KnowledgeBaseRef 均幂等（重复引用复用已有记录） */
    private void writeReferences(Agent agent, JsonNode declaration) {
        addAssetReferences(agent, declaration.get("skills"));
        addAssetReferences(agent, declaration.get("mcpTools"));
        bindKnowledgeBaseRefs(agent, declaration.get("knowledgeBases"));
    }

    private void addAssetReferences(Agent agent, JsonNode items) {
        if (items == null || !items.isArray()) {
            return;
        }
        for (JsonNode item : items) {
            ReferenceAddReq refReq = new ReferenceAddReq();
            refReq.setProjectId(agent.getProjectId());
            refReq.setAssetVersion(item.get("version").asText());
            refReq.setAgentId(agent.getId());
            assetReferenceService.add(item.get("assetId").asLong(), refReq);
        }
    }

    private void bindKnowledgeBaseRefs(Agent agent, JsonNode knowledgeBases) {
        if (knowledgeBases == null || !knowledgeBases.isArray()) {
            return;
        }
        // 幂等：已存在该 agent 级 ref 的知识库跳过（checkKbAllowed 可能由项目级 ref 放行）
        Set<Long> boundKbIds = knowledgeAccessService.listRefs(agent.getProjectId(), agent.getId())
                .stream().map(KnowledgeBaseRef::getKbId).collect(Collectors.toSet());
        for (JsonNode item : knowledgeBases) {
            long kbId = item.get("kbId").asLong();
            if (boundKbIds.contains(kbId)) {
                continue;
            }
            KbRefBindReq bindReq = new KbRefBindReq();
            bindReq.setKbId(kbId);
            bindReq.setAgentId(agent.getId());
            knowledgeBaseRefService.bind(agent.getProjectId(), bindReq);
            boundKbIds.add(kbId);
        }
    }
}
