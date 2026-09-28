package com.bosc.agentops.agentdev.controller;

import com.bosc.agentops.agentdev.dto.ScaffoldDetailReq;
import com.bosc.agentops.agentdev.entity.ScaffoldTemplate;
import com.bosc.agentops.agentdev.service.ScaffoldService;
import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 脚手架模板：平台级静态资源（不归属项目），所有认证用户可读；
 * 只提供模板元数据与文件内容，不做远程代码生成。
 */
@RestController
@RequestMapping("/v1/api/scaffolds")
public class ScaffoldController {

    private final ScaffoldService scaffoldService;

    public ScaffoldController(ScaffoldService scaffoldService) {
        this.scaffoldService = scaffoldService;
    }

    @PostMapping("/list")
    @RequirePermission(value = "agent:read", projectScoped = false)
    public ApiResponse<List<ScaffoldTemplate>> list() {
        return ApiResponse.ok(scaffoldService.list());
    }

    @PostMapping("/detail")
    @RequirePermission(value = "agent:read", projectScoped = false)
    public ApiResponse<ScaffoldTemplate> detail(@Valid @RequestBody ScaffoldDetailReq req) {
        return ApiResponse.ok(scaffoldService.detail(req.getCode()));
    }
}
