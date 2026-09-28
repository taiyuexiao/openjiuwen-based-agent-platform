package com.bosc.agentops.assethub.controller;

import com.bosc.agentops.assethub.dto.HttpApiConvertReq;
import com.bosc.agentops.assethub.dto.HttpApiRegisterReq;
import com.bosc.agentops.assethub.dto.ProjectScopedReq;
import com.bosc.agentops.assethub.dto.ToolDraft;
import com.bosc.agentops.assethub.entity.Asset;
import com.bosc.agentops.assethub.service.HttpApiService;
import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 历史 HTTP API（OpenAPI 3.x）注册与转换为 MCP_TOOL。 */
@RestController
@RequestMapping("/v1/api/http-apis")
public class HttpApiController {

    private final HttpApiService httpApiService;

    public HttpApiController(HttpApiService httpApiService) {
        this.httpApiService = httpApiService;
    }

    @PostMapping("/register")
    @RequirePermission("asset:create")
    public ApiResponse<Asset> register(@Valid @RequestBody HttpApiRegisterReq req) {
        return ApiResponse.ok(httpApiService.register(req));
    }

    @PostMapping("/{id}/convert")
    @RequirePermission("asset:create")
    public ApiResponse<List<Asset>> convert(@PathVariable Long id,
                                            @Valid @RequestBody HttpApiConvertReq req) {
        return ApiResponse.ok(httpApiService.convert(id, req));
    }

    @PostMapping("/{id}/tools/list")
    @RequirePermission("asset:read")
    public ApiResponse<List<ToolDraft>> listTools(@PathVariable Long id,
                                                  @Valid @RequestBody ProjectScopedReq req) {
        return ApiResponse.ok(httpApiService.listTools(id, req));
    }
}
