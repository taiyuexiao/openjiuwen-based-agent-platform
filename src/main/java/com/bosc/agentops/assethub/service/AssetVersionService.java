package com.bosc.agentops.assethub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.assethub.dto.DraftVersionPublishReq;
import com.bosc.agentops.assethub.dto.ProjectScopedReq;
import com.bosc.agentops.assethub.dto.VersionPublishReq;
import com.bosc.agentops.assethub.entity.Asset;
import com.bosc.agentops.assethub.entity.AssetStatus;
import com.bosc.agentops.assethub.entity.AssetType;
import com.bosc.agentops.assethub.entity.AssetVersion;
import com.bosc.agentops.assethub.entity.AssetVersionStatus;
import com.bosc.agentops.assethub.mapper.AssetMapper;
import com.bosc.agentops.assethub.mapper.AssetVersionMapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 资产版本发布与查询。publish 创建即 PUBLISHED 且不可变；publishDraft 发布上传产生的 DRAFT 版本。
 */
@Service
public class AssetVersionService {

    private final AssetVersionMapper assetVersionMapper;
    private final AssetMapper assetMapper;
    private final AssetService assetService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AssetVersionService(AssetVersionMapper assetVersionMapper,
                               AssetMapper assetMapper,
                               AssetService assetService,
                               AuditService auditService,
                               ObjectMapper objectMapper) {
        this.assetVersionMapper = assetVersionMapper;
        this.assetMapper = assetMapper;
        this.assetService = assetService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AssetVersion publish(Long assetId, VersionPublishReq req) {
        String userId = RequestContext.currentUserId();
        Asset asset = assetService.getOrThrow(assetId);
        assetService.requireOwnerProject(asset, req.getProjectId());
        if (asset.getStatus() == AssetStatus.OFFLINE) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "资产已下线，禁止发布新版本: " + assetId);
        }
        AssetVersion version = new AssetVersion();
        version.setAssetId(assetId);
        version.setVersion(req.getVersion());
        version.setDefinition(JsonSupport.toJson(objectMapper, req.getDefinition()));
        version.setStatus(AssetVersionStatus.PUBLISHED);
        version.setPublishedBy(userId);
        version.setPublishedAt(LocalDateTime.now());
        version.setCreatedBy(userId);
        try {
            assetVersionMapper.insert(version);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE,
                    "版本已存在且不可变，请使用新版本号: " + req.getVersion());
        }
        auditService.record(AssetService.MODULE, "version.publish", "asset_version", version.getId(),
                Map.of("assetId", assetId, "version", version.getVersion()));
        return version;
    }

    /**
     * 发布上传产生的 DRAFT 版本：仅 DRAFT 可发布（PUBLISHED/OFFLINE → 40902）；
     * 资产须非 OFFLINE；MCP_TOOL 仍强制归属 MCP_SERVICE（与 AssetService.publish 一致）；
     * 资产整体仍为 DRAFT 时联动置 PUBLISHED（与既有 publish 最终效果对齐）。
     */
    @Transactional
    public AssetVersion publishDraft(DraftVersionPublishReq req) {
        String userId = RequestContext.currentUserId();
        Asset asset = assetService.getOrThrow(req.getAssetId());
        assetService.requireOwnerProject(asset, req.getProjectId());
        if (asset.getStatus() == AssetStatus.OFFLINE) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "资产已下线，禁止发布版本: " + asset.getId());
        }
        AssetVersion version = assetService.getVersionOrThrow(asset.getId(), req.getVersion());
        if (version.getStatus() == AssetVersionStatus.PUBLISHED) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "版本已是发布状态: " + req.getVersion());
        }
        if (version.getStatus() == AssetVersionStatus.OFFLINE) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "版本已下线，不能重新发布: " + req.getVersion());
        }
        if (asset.getAssetType() == AssetType.MCP_TOOL && asset.getParentAssetId() == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "MCP_TOOL 发布前必须归属某个 MCP_SERVICE: " + asset.getId());
        }
        version.setStatus(AssetVersionStatus.PUBLISHED);
        version.setPublishedBy(userId);
        version.setPublishedAt(LocalDateTime.now());
        assetVersionMapper.updateById(version);
        if (asset.getStatus() == AssetStatus.DRAFT) {
            asset.setStatus(AssetStatus.PUBLISHED);
            assetMapper.updateById(asset);
        }
        auditService.record(AssetService.MODULE, "version.publish-draft", "asset_version", version.getId(),
                Map.of("assetId", asset.getId(), "version", version.getVersion()));
        return version;
    }

    public List<AssetVersion> list(Long assetId, ProjectScopedReq req) {
        Asset asset = assetService.getOrThrow(assetId);
        assetService.requireVisible(asset, req.getProjectId());
        return assetVersionMapper.selectList(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, assetId)
                .orderByAsc(AssetVersion::getId));
    }
}
