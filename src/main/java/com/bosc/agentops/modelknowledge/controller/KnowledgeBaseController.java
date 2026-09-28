package com.bosc.agentops.modelknowledge.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.modelknowledge.dto.CatalogIdReq;
import com.bosc.agentops.modelknowledge.dto.KnowledgeBaseCreateReq;
import com.bosc.agentops.modelknowledge.dto.KnowledgeBaseUpdateReq;
import com.bosc.agentops.modelknowledge.entity.KnowledgeBase;
import com.bosc.agentops.modelknowledge.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库平台级目录。
 * create/update/disable 为平台级目录管理：本期约定「认证用户 + 记录操作人」（与 project:create 同级，
 * PermissionService 对平台级权限放行），后续接平台运营角色收紧。
 */
@RestController
@RequestMapping("/v1/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/create")
    @RequirePermission(value = "kb:manage", projectScoped = false)
    public ApiResponse<KnowledgeBase> create(@Valid @RequestBody KnowledgeBaseCreateReq req) {
        return ApiResponse.ok(knowledgeBaseService.create(req));
    }

    @PostMapping("/update")
    @RequirePermission(value = "kb:manage", projectScoped = false)
    public ApiResponse<KnowledgeBase> update(@Valid @RequestBody KnowledgeBaseUpdateReq req) {
        return ApiResponse.ok(knowledgeBaseService.update(req));
    }

    @PostMapping("/disable")
    @RequirePermission(value = "kb:manage", projectScoped = false)
    public ApiResponse<KnowledgeBase> disable(@Valid @RequestBody CatalogIdReq req) {
        return ApiResponse.ok(knowledgeBaseService.disable(req.getId()));
    }

    @PostMapping("/list")
    @RequirePermission(value = "kb:read", projectScoped = false)
    public ApiResponse<List<KnowledgeBase>> list() {
        return ApiResponse.ok(knowledgeBaseService.list());
    }
}
