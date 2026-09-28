package com.bosc.agentops.agentdev.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.agentdev.dto.AgentSquareItem;
import com.bosc.agentops.agentdev.dto.AgentSquareListReq;
import com.bosc.agentops.agentdev.entity.Agent;
import com.bosc.agentops.agentdev.entity.AgentStatus;
import com.bosc.agentops.agentdev.entity.AgentVersion;
import com.bosc.agentops.agentdev.entity.AgentVisibility;
import com.bosc.agentops.agentdev.mapper.AgentMapper;
import com.bosc.agentops.agentdev.mapper.AgentVersionMapper;
import com.bosc.agentops.project.entity.Project;
import com.bosc.agentops.project.mapper.ProjectMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 广场：全平台可见性查询（visibility=PUBLIC 且 status=ACTIVE）。
 * 平台级接口，不做项目过滤；项目名与版本摘要在此聚合。
 */
@Service
public class AgentSquareService {

    private final AgentMapper agentMapper;
    private final AgentVersionMapper agentVersionMapper;
    private final ProjectMapper projectMapper;

    public AgentSquareService(AgentMapper agentMapper,
                              AgentVersionMapper agentVersionMapper,
                              ProjectMapper projectMapper) {
        this.agentMapper = agentMapper;
        this.agentVersionMapper = agentVersionMapper;
        this.projectMapper = projectMapper;
    }

    public List<AgentSquareItem> list(AgentSquareListReq req) {
        String keyword = req == null ? null : req.getKeyword();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<Agent>()
                .eq(Agent::getVisibility, AgentVisibility.PUBLIC)
                .eq(Agent::getStatus, AgentStatus.ACTIVE)
                .and(hasKeyword, w -> w.like(Agent::getCode, keyword)
                        .or().like(Agent::getName, keyword)
                        .or().like(Agent::getDescription, keyword))
                .orderByAsc(Agent::getId);
        List<Agent> agents = agentMapper.selectList(wrapper);
        if (agents.isEmpty()) {
            return List.of();
        }

        List<Long> projectIds = agents.stream().map(Agent::getProjectId).distinct().toList();
        Map<Long, String> projectNames = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));

        List<Long> agentIds = agents.stream().map(Agent::getId).toList();
        Map<Long, List<AgentVersion>> versionsByAgent = agentVersionMapper.selectList(
                        new LambdaQueryWrapper<AgentVersion>().in(AgentVersion::getAgentId, agentIds))
                .stream().collect(Collectors.groupingBy(AgentVersion::getAgentId));

        return agents.stream().map(agent -> {
            AgentSquareItem item = new AgentSquareItem();
            item.setId(agent.getId());
            item.setCode(agent.getCode());
            item.setName(agent.getName());
            item.setDescription(agent.getDescription());
            item.setProjectId(agent.getProjectId());
            item.setProjectName(projectNames.get(agent.getProjectId()));
            item.setAccessMode(agent.getAccessMode());
            item.setCreatedAt(agent.getCreatedAt());
            List<AgentVersion> versions = versionsByAgent.getOrDefault(agent.getId(), List.of());
            item.setVersionCount(versions.size());
            versions.stream().max(Comparator.comparing(AgentVersion::getId))
                    .ifPresent(latest -> item.setLatestVersion(latest.getVersion()));
            return item;
        }).toList();
    }
}
