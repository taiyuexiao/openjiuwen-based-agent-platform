package com.bosc.agentops.agentdev.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.dto.AgentCreateReq;
import com.bosc.agentops.agentdev.dto.AgentDetailResp;
import com.bosc.agentops.agentdev.dto.AgentListReq;
import com.bosc.agentops.agentdev.dto.AgentOperateReq;
import com.bosc.agentops.agentdev.dto.AgentUpdateReq;
import com.bosc.agentops.agentdev.entity.AccessMode;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.entity.AgentStatus;
import com.bosc.agentops.agentdev.entity.AgentVisibility;
import com.bosc.agentops.agentdev.mapper.AgentMapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.project.entity.Project;
import com.bosc.agentops.project.service.ProjectService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Agent CRUD 与生命周期（archive）。
 * 接入方式边界：NATIVE=完整声明校验+轨迹观测；ADAPTED=声明可空，空声明登记标记能力降级；
 * HOSTED=不登记声明，必须提供运行入口与健康检查地址，平台仅承诺服务调用/生命周期/基础日志。
 */
@Service
public class AgentService {

    static final String MODULE = "agent-dev";

    static final String NOTE_NATIVE = "原生模式：声明必填且全量校验，支持轨迹观测";
    static final String NOTE_ADAPTED = "适配模式：声明可空；空声明登记的版本标记 capabilityDegraded=true（能力降级）";
    static final String NOTE_HOSTED = "托管模式：不登记声明清单与版本，平台仅承诺服务调用、生命周期与基础日志";

    private final AgentMapper agentMapper;
    private final ProjectService projectService;
    private final AuditService auditService;

    public AgentService(AgentMapper agentMapper, ProjectService projectService, AuditService auditService) {
        this.agentMapper = agentMapper;
        this.projectService = projectService;
        this.auditService = auditService;
    }

    @Transactional
    public Agent create(AgentCreateReq req) {
        String userId = RequestContext.currentUserId();
        Project project = projectService.getOrThrow(req.getProjectId());
        projectService.requireActive(project);
        AccessMode accessMode = req.getAccessMode() == null ? AccessMode.NATIVE : req.getAccessMode();
        if (accessMode == AccessMode.HOSTED) {
            if (isBlank(req.getRuntimeEndpoint()) || isBlank(req.getHealthEndpoint())) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "HOSTED（运行托管）模式必须提供 runtimeEndpoint 与 healthEndpoint");
            }
        }
        Agent agent = new Agent();
        agent.setCode(req.getCode());
        agent.setName(req.getName());
        agent.setProjectId(req.getProjectId());
        agent.setAccessMode(accessMode);
        // 运行入口/健康检查仅 HOSTED 有意义，其余模式不记录
        agent.setRuntimeEndpoint(accessMode == AccessMode.HOSTED ? req.getRuntimeEndpoint() : null);
        agent.setHealthEndpoint(accessMode == AccessMode.HOSTED ? req.getHealthEndpoint() : null);
        agent.setDescription(req.getDescription());
        agent.setStatus(AgentStatus.ACTIVE);
        agent.setVisibility(AgentVisibility.PROJECT);
        agent.setCreatedBy(userId);
        try {
            agentMapper.insert(agent);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "项目内 Agent code 已存在: " + req.getCode());
        }
        auditService.record(MODULE, "agent.create", "agent", agent.getId(),
                Map.of("code", agent.getCode(), "name", agent.getName(),
                        "accessMode", accessMode.name(), "projectId", agent.getProjectId()));
        return agent;
    }

    @Transactional
    public Agent update(AgentUpdateReq req) {
        Agent agent = getOrThrow(req.getId());
        requireOwnerProject(agent, req.getProjectId());
        if (agent.getStatus() == AgentStatus.ARCHIVED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "Agent 已归档，禁止修改: " + agent.getId());
        }
        if (req.getName() != null) {
            agent.setName(req.getName());
        }
        if (req.getDescription() != null) {
            agent.setDescription(req.getDescription());
        }
        if (agent.getAccessMode() == AccessMode.HOSTED) {
            if (req.getRuntimeEndpoint() != null) {
                agent.setRuntimeEndpoint(req.getRuntimeEndpoint());
            }
            if (req.getHealthEndpoint() != null) {
                agent.setHealthEndpoint(req.getHealthEndpoint());
            }
        }
        agentMapper.updateById(agent);
        auditService.record(MODULE, "agent.update", "agent", agent.getId(),
                Map.of("code", agent.getCode()));
        return agent;
    }

    @Transactional
    public Agent archive(AgentOperateReq req) {
        Agent agent = getOrThrow(req.getId());
        requireOwnerProject(agent, req.getProjectId());
        if (agent.getStatus() == AgentStatus.ARCHIVED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "Agent 已是归档状态: " + agent.getId());
        }
        agent.setStatus(AgentStatus.ARCHIVED);
        agentMapper.updateById(agent);
        auditService.record(MODULE, "agent.archive", "agent", agent.getId(),
                Map.of("code", agent.getCode()));
        return agent;
    }

    public AgentDetailResp detail(AgentOperateReq req) {
        Agent agent = getOrThrow(req.getId());
        requireOwnerProject(agent, req.getProjectId());
        return new AgentDetailResp(agent, capabilityNote(agent.getAccessMode()));
    }

    /** 发布到 Agent 广场：visibility 置 PUBLIC，全平台认证用户可见 */
    @Transactional
    public Agent publish(AgentOperateReq req) {
        return changeVisibility(req, AgentVisibility.PUBLIC, "agent.publish");
    }

    /** 从 Agent 广场下架：visibility 置回 PROJECT */
    @Transactional
    public Agent unpublish(AgentOperateReq req) {
        return changeVisibility(req, AgentVisibility.PROJECT, "agent.unpublish");
    }

    private Agent changeVisibility(AgentOperateReq req, AgentVisibility target, String auditAction) {
        Agent agent = getOrThrow(req.getId());
        requireOwnerProject(agent, req.getProjectId());
        if (agent.getStatus() == AgentStatus.ARCHIVED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "Agent 已归档，禁止变更可见性: " + agent.getId());
        }
        if (agent.getVisibility() == target) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "Agent 已是 " + target + " 可见性: " + agent.getId());
        }
        agent.setVisibility(target);
        agentMapper.updateById(agent);
        auditService.record(MODULE, auditAction, "agent", agent.getId(),
                Map.of("code", agent.getCode(), "visibility", target.name()));
        return agent;
    }

    public List<Agent> list(AgentListReq req) {
        projectService.getOrThrow(req.getProjectId());
        return agentMapper.selectList(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getProjectId, req.getProjectId())
                .eq(req.getAccessMode() != null, Agent::getAccessMode, req.getAccessMode())
                .eq(req.getStatus() != null, Agent::getStatus, req.getStatus())
                .orderByAsc(Agent::getId));
    }

    public Agent getOrThrow(Long id) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 不存在: " + id);
        }
        return agent;
    }

    /** 操作必须落在 Agent 所属项目上（防跨项目冒用权限） */
    public void requireOwnerProject(Agent agent, Long projectId) {
        if (!agent.getProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "仅所属项目可执行该操作: agentId=" + agent.getId() + ", projectId=" + agent.getProjectId());
        }
    }

    static String capabilityNote(AccessMode accessMode) {
        return switch (accessMode) {
            case NATIVE -> NOTE_NATIVE;
            case ADAPTED -> NOTE_ADAPTED;
            case HOSTED -> NOTE_HOSTED;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
