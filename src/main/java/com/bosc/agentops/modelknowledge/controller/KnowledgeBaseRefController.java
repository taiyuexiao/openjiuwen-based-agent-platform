package com.bosc.agentops.modelknowledge.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.modelknowledge.dto.KbRefBindReq;
import com.bosc.agentops.modelknowledge.dto.KbRefListReq;
import com.bosc.agentops.modelknowledge.dto.KbRefUnbindReq;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBaseRef;
import com.bosc.agentops.modelknowledge.service.KnowledgeBaseRefService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库引用绑定：保留知识库-项目-Agent 关联关系。scope 从路径变量 projectId 解析。
 */
@RestController
@RequestMapping("/v1/api/projects/{projectId}/kb-refs")
public class KnowledgeBaseRefController {

    private final KnowledgeBaseRefService knowledgeBaseRefService;

    public KnowledgeBaseRefController(KnowledgeBaseRefService knowledgeBaseRefService) {
        this.knowledgeBaseRefService = knowledgeBaseRefService;
    }

    @PostMapping("/bind")
    @RequirePermission("kb:grant")
    public ApiResponse<KnowledgeBaseRef> bind(@PathVariable Long projectId,
                                              @Valid @RequestBody KbRefBindReq req) {
        return ApiResponse.ok(knowledgeBaseRefService.bind(projectId, req));
    }

    @PostMapping("/unbind")
    @RequirePermission("kb:grant")
    public ApiResponse<Void> unbind(@PathVariable Long projectId,
                                    @Valid @RequestBody KbRefUnbindReq req) {
        knowledgeBaseRefService.unbind(projectId, req.getRefId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/list")
    @RequirePermission("kb:read")
    public ApiResponse<List<KnowledgeBaseRef>> list(@PathVariable Long projectId,
                                                    @RequestBody(required = false) KbRefListReq req) {
        return ApiResponse.ok(knowledgeBaseRefService.list(projectId, req == null ? null : req.getAgentId()));
    }
}
