package com.bosc.agentops.assethub.controller;

import com.bosc.agentops.assethub.dto.AssetUploadResp;
import com.bosc.agentops.assethub.service.AssetUploadService;
import com.bosc.agentops.common.api.ApiResponse;
import com.bosc.agentops.common.permission.RequirePermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 资产文件上传（multipart）。权限 scope 从 projectId 参数解析；
 * 上传只产生 DRAFT 资产/版本，发布走既有 publish 接口。
 */
@RestController
@RequestMapping("/v1/api/assets")
public class AssetUploadController {

    private final AssetUploadService assetUploadService;

    public AssetUploadController(AssetUploadService assetUploadService) {
        this.assetUploadService = assetUploadService;
    }

    @PostMapping("/upload")
    @RequirePermission("asset:create")
    public ApiResponse<AssetUploadResp> upload(@RequestParam("file") MultipartFile file,
                                               @RequestParam("projectId") Long projectId,
                                               @RequestParam("assetType") String assetType,
                                               @RequestParam("name") String name,
                                               @RequestParam(value = "code", required = false) String code,
                                               @RequestParam(value = "version", required = false) String version) {
        return ApiResponse.ok(assetUploadService.upload(file, projectId, assetType, name, code, version));
    }
}
