package com.bosc.agentops.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.entity.AccessMode;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.entity.AgentVersion;
import com.bosc.agentops.agentdev.mapper.AgentVersionMapper;
import com.bosc.agentops.agentdev.service.AgentService;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.delivery.dto.ArtifactListReq;
import com.bosc.agentops.delivery.dto.ArtifactRegisterReq;
import com.bosc.agentops.delivery.dto.DeliveryIdReq;
import com.bosc.agentops.delivery.entity.Artifact;
import com.bosc.agentops.delivery.mapper.ArtifactMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 制品登记与查询。制品不可变：无 update 接口，(agentId, agentVersion) 唯一，重建需登记新版本。
 * 可追溯约束：非 HOSTED 模式要求对应 AgentVersion 已登记（HOSTED 不登记版本，仅按版本号标签登记制品）。
 */
@Service
public class ArtifactService {

    static final String MODULE = "delivery";

    private final ArtifactMapper artifactMapper;
    private final AgentService agentService;
    private final AgentVersionMapper agentVersionMapper;
    private final AuditService auditService;

    public ArtifactService(ArtifactMapper artifactMapper, AgentService agentService,
                           AgentVersionMapper agentVersionMapper, AuditService auditService) {
        this.artifactMapper = artifactMapper;
        this.agentService = agentService;
        this.agentVersionMapper = agentVersionMapper;
        this.auditService = auditService;
    }

    @Transactional
    public Artifact register(ArtifactRegisterReq req) {
        String userId = RequestContext.currentUserId();
        Agent agent = agentService.getOrThrow(req.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        if (agent.getAccessMode() != AccessMode.HOSTED) {
            AgentVersion version = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersion>()
                    .eq(AgentVersion::getAgentId, agent.getId())
                    .eq(AgentVersion::getVersion, req.getAgentVersion()));
            if (version == null) {
                throw new BizException(ErrorCode.NOT_FOUND,
                        "Agent 版本不存在，制品必须关联已登记版本: agentId=" + agent.getId()
                                + ", version=" + req.getAgentVersion());
            }
        }
        Artifact artifact = new Artifact();
        artifact.setAgentId(agent.getId());
        artifact.setAgentVersion(req.getAgentVersion());
        artifact.setCodeCommit(req.getCodeCommit());
        artifact.setImageDigest(req.getImageDigest());
        artifact.setConfigDigest(req.getConfigDigest());
        artifact.setEvaluationRef(req.getEvaluationRef());
        artifact.setBuiltAt(req.getBuiltAt() == null ? LocalDateTime.now() : req.getBuiltAt());
        artifact.setCreatedBy(userId);
        try {
            artifactMapper.insert(artifact);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "该 Agent 版本已登记制品（制品不可变，重建请登记新版本）: agentId=" + agent.getId()
                            + ", version=" + req.getAgentVersion());
        }
        auditService.record(MODULE, "artifact.register", "delivery_artifact", artifact.getId(),
                Map.of("agentId", artifact.getAgentId(), "agentVersion", artifact.getAgentVersion()));
        return artifact;
    }

    public Artifact detail(DeliveryIdReq req) {
        Artifact artifact = getOrThrow(req.getId());
        Agent agent = agentService.getOrThrow(artifact.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        return artifact;
    }

    public List<Artifact> list(ArtifactListReq req) {
        Agent agent = agentService.getOrThrow(req.getAgentId());
        agentService.requireOwnerProject(agent, req.getProjectId());
        return artifactMapper.selectList(new LambdaQueryWrapper<Artifact>()
                .eq(Artifact::getAgentId, req.getAgentId())
                .orderByAsc(Artifact::getId));
    }

    public Artifact getOrThrow(Long id) {
        Artifact artifact = artifactMapper.selectById(id);
        if (artifact == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "制品不存在: " + id);
        }
        return artifact;
    }

    /** 制品完整性检查（发布门禁第 2 项）：返回全部缺失字段名 */
    public static List<String> missingFields(Artifact artifact) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("codeCommit", artifact.getCodeCommit());
        fields.put("imageDigest", artifact.getImageDigest());
        fields.put("configDigest", artifact.getConfigDigest());
        fields.put("evaluationRef", artifact.getEvaluationRef());
        return fields.entrySet().stream()
                .filter(e -> JsonSupport.isBlank(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
