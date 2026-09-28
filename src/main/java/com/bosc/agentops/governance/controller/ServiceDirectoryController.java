package com.bosc.agentops.governance.controller;

import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import com.bosc.agentops.governance.dto.ServiceDirectoryEntry;
import com.bosc.agentops.governance.dto.ServiceDirectoryReq;
import com.bosc.agentops.governance.service.ServiceDirectoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务目录：对调用方暴露的已发布 Agent 服务（标识/环境/版本/地址/认证方式）。
 */
@RestController
@RequestMapping("/v1/api/service-directory")
public class ServiceDirectoryController {

    private final ServiceDirectoryService serviceDirectoryService;

    public ServiceDirectoryController(ServiceDirectoryService serviceDirectoryService) {
        this.serviceDirectoryService = serviceDirectoryService;
    }

    @PostMapping("/list")
    @RequirePermission("gov:read")
    public ApiResponse<List<ServiceDirectoryEntry>> list(@Valid @RequestBody ServiceDirectoryReq req) {
        return ApiResponse.ok(serviceDirectoryService.list(req));
    }
}
