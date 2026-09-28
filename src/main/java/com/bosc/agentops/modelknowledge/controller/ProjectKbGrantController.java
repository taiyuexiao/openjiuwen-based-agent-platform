package com.bosc.agentops.modelknowledge.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.modelknowledge.dto.KbGrantReq;
import com.bosc.agentops.modelknowledge.dto.KbGrantRevokeReq;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBaseGrant;
import com.bosc.agentops.modelknowledge.service.KnowledgeBaseGrantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目级知识库授权。scope 从路径变量 projectId 解析（参数名约定）。
 */
@RestController
@RequestMapping("/v1/api/projects/{projectId}/kb-grants")
public class ProjectKbGrantController {

    private final KnowledgeBaseGrantService knowledgeBaseGrantService;

    public ProjectKbGrantController(KnowledgeBaseGrantService knowledgeBaseGrantService) {
        this.knowledgeBaseGrantService = knowledgeBaseGrantService;
    }

    @PostMapping("/grant")
    @RequirePermission("kb:grant")
    public ApiResponse<KnowledgeBaseGrant> grant(@PathVariable Long projectId,
                                                 @Valid @RequestBody KbGrantReq req) {
        return ApiResponse.ok(knowledgeBaseGrantService.grant(projectId, req));
    }

    @PostMapping("/revoke")
    @RequirePermission("kb:grant")
    public ApiResponse<Void> revoke(@PathVariable Long projectId,
                                    @Valid @RequestBody KbGrantRevokeReq req) {
        knowledgeBaseGrantService.revoke(projectId, req.getKbId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/list")
    @RequirePermission("kb:read")
    public ApiResponse<List<KnowledgeBaseGrant>> list(@PathVariable Long projectId) {
        return ApiResponse.ok(knowledgeBaseGrantService.list(projectId));
    }
}
