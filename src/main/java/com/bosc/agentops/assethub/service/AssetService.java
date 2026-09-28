package com.bosc.agentops.assethub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.assethub.dto.AssetCreateReq;
import com.bosc.agentops.assethub.dto.AssetListReq;
import com.bosc.agentops.assethub.dto.AssetOperateReq;
import com.bosc.agentops.assethub.dto.AssetUpdateReq;
import com.bosc.agentops.assethub.entity.Asset;
import com.bosc.agentops.assethub.entity.AssetGrant;
import com.bosc.agentops.assethub.entity.AssetStatus;
import com.bosc.agentops.assethub.entity.AssetType;
import com.bosc.agentops.assethub.entity.AssetVersion;
import com.bosc.agentops.assethub.entity.AssetVersionStatus;
import com.bosc.agentops.assethub.entity.AssetVisibility;
import com.bosc.agentops.assethub.mapper.AssetGrantMapper;
import com.bosc.agentops.assethub.mapper.AssetMapper;
import com.bosc.agentops.assethub.mapper.AssetVersionMapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.project.entity.Project;
import com.bosc.agentops.project.entity.Role;
import com.bosc.agentops.project.service.ProjectAccessService;
import com.bosc.agentops.project.service.ProjectService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 资产 CRUD 与生命周期（publish/offline）。
 * 可见性：PROJECT=仅归属项目；SHARED=归属项目 + AssetGrant 授权项目。默认不全局可见。
 */
@Service
public class AssetService {

    static final String MODULE = "asset-hub";

    private final AssetMapper assetMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final AssetGrantMapper assetGrantMapper;
    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;
    private final AuditService auditService;

    public AssetService(AssetMapper assetMapper,
                        AssetVersionMapper assetVersionMapper,
                        AssetGrantMapper assetGrantMapper,
                        ProjectService projectService,
                        ProjectAccessService projectAccessService,
                        AuditService auditService) {
        this.assetMapper = assetMapper;
        this.assetVersionMapper = assetVersionMapper;
        this.assetGrantMapper = assetGrantMapper;
        this.projectService = projectService;
        this.projectAccessService = projectAccessService;
        this.auditService = auditService;
    }

    @Transactional
    public Asset create(AssetCreateReq req) {
        String userId = RequestContext.currentUserId();
        Project project = projectService.getOrThrow(req.getProjectId());
        projectService.requireActive(project);
        if (req.getParentAssetId() != null) {
            requireMcpServiceParent(req.getParentAssetId());
        }
        Asset asset = new Asset();
        asset.setCode(req.getCode());
        asset.setName(req.getName());
        asset.setAssetType(req.getAssetType());
        asset.setParentAssetId(req.getAssetType() == AssetType.MCP_TOOL ? req.getParentAssetId() : null);
        asset.setOwnerProjectId(req.getProjectId());
        asset.setOwnerUserId(userId);
        asset.setVisibility(req.getVisibility());
        asset.setStatus(AssetStatus.DRAFT);
        asset.setDescription(req.getDescription());
        asset.setCreatedBy(userId);
        try {
            assetMapper.insert(asset);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE, "资产 code 已存在: " + req.getCode());
        }
        auditService.record(MODULE, "asset.create", "asset", asset.getId(),
                Map.of("code", asset.getCode(), "name", asset.getName(),
                        "assetType", asset.getAssetType().name(),
                        "projectId", asset.getOwnerProjectId()));
        return asset;
    }

    @Transactional
    public Asset update(AssetUpdateReq req) {
        Asset asset = getOrThrow(req.getId());
        requireOwnerProject(asset, req.getProjectId());
        if (asset.getStatus() == AssetStatus.OFFLINE) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "资产已下线，禁止修改: " + asset.getId());
        }
        if (req.getVersion() != null || req.getDefinition() != null) {
            // 版本不可变：任何修改已发布版本 definition 的尝试都拒绝，变更只能发新版本
            if (req.getVersion() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "修改 definition 必须指定版本号");
            }
            AssetVersion version = getVersionOrThrow(asset.getId(), req.getVersion());
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "版本 " + version.getVersion() + " 当前状态 " + version.getStatus()
                            + "，definition 不可修改，请发布新版本");
        }
        if (req.getName() != null) {
            asset.setName(req.getName());
        }
        if (req.getDescription() != null) {
            asset.setDescription(req.getDescription());
        }
        if (req.getVisibility() != null) {
            asset.setVisibility(req.getVisibility());
        }
        assetMapper.updateById(asset);
        auditService.record(MODULE, "asset.update", "asset", asset.getId(),
                Map.of("code", asset.getCode()));
        return asset;
    }

    @Transactional
    public Asset publish(AssetOperateReq req) {
        Asset asset = getOrThrow(req.getId());
        requireOwnerProject(asset, req.getProjectId());
        if (asset.getStatus() == AssetStatus.PUBLISHED) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "资产已是发布状态: " + asset.getId());
        }
        if (asset.getStatus() == AssetStatus.OFFLINE) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "资产已下线，不能重新发布: " + asset.getId());
        }
        if (asset.getAssetType() == AssetType.MCP_TOOL && asset.getParentAssetId() == null) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "MCP_TOOL 发布前必须归属某个 MCP_SERVICE: " + asset.getId());
        }
        Long publishedVersions = assetVersionMapper.selectCount(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, asset.getId())
                .eq(AssetVersion::getStatus, AssetVersionStatus.PUBLISHED));
        if (publishedVersions == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "资产发布前至少需要一个已发布版本: " + asset.getId());
        }
        asset.setStatus(AssetStatus.PUBLISHED);
        assetMapper.updateById(asset);
        auditService.record(MODULE, "asset.publish", "asset", asset.getId(),
                Map.of("code", asset.getCode()));
        return asset;
    }

    @Transactional
    public Asset offline(AssetOperateReq req) {
        Asset asset = getOrThrow(req.getId());
        requireOwnerProject(asset, req.getProjectId());
        requireOwnerOrAdmin(asset, "offline");
        if (asset.getStatus() == AssetStatus.OFFLINE) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "资产已是下线状态: " + asset.getId());
        }
        // OFFLINE 语义：checkUsable=false、禁止新引用；已有 AssetReference 记录保留可查
        asset.setStatus(AssetStatus.OFFLINE);
        assetMapper.updateById(asset);
        auditService.record(MODULE, "asset.offline", "asset", asset.getId(),
                Map.of("code", asset.getCode()));
        return asset;
    }

    public Asset detail(AssetOperateReq req) {
        Asset asset = getOrThrow(req.getId());
        requireVisible(asset, req.getProjectId());
        return asset;
    }

    /** 只返回调用人所在项目可见的资产：归属该项目，或 SHARED 且已授权该项目 */
    public List<Asset> list(AssetListReq req) {
        projectService.getOrThrow(req.getProjectId());
        List<Long> grantedAssetIds = assetGrantMapper.selectList(new LambdaQueryWrapper<AssetGrant>()
                        .eq(AssetGrant::getToProjectId, req.getProjectId()))
                .stream().map(AssetGrant::getAssetId).toList();
        // 无授权时用不可能命中的哨兵值，保证 SHARED 分支不可满足（否则所有 SHARED 资产都会误命中）
        List<Long> grantedOrSentinel = grantedAssetIds.isEmpty() ? List.of(-1L) : grantedAssetIds;
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .eq(req.getAssetType() != null, Asset::getAssetType, req.getAssetType())
                .and(w -> w.eq(Asset::getOwnerProjectId, req.getProjectId())
                        .or(o -> o.eq(Asset::getVisibility, AssetVisibility.SHARED)
                                .in(Asset::getId, grantedOrSentinel)))
                .orderByAsc(Asset::getId);
        return assetMapper.selectList(wrapper);
    }

    public Asset getOrThrow(Long id) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "资产不存在: " + id);
        }
        return asset;
    }

    public AssetVersion getVersionOrThrow(Long assetId, String version) {
        AssetVersion assetVersion = assetVersionMapper.selectOne(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, assetId)
                .eq(AssetVersion::getVersion, version));
        if (assetVersion == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "资产版本不存在: assetId=" + assetId + ", version=" + version);
        }
        return assetVersion;
    }

    /** 操作必须落在资产归属项目上（防跨项目冒用权限） */
    public void requireOwnerProject(Asset asset, Long projectId) {
        if (!asset.getOwnerProjectId().equals(projectId)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "仅归属项目可执行该操作: assetId=" + asset.getId() + ", ownerProjectId=" + asset.getOwnerProjectId());
        }
    }

    /** 可见性判定：归属项目可见；SHARED 且存在该项目授权可见；其余拒绝 */
    public void requireVisible(Asset asset, Long projectId) {
        if (asset.getOwnerProjectId().equals(projectId)) {
            return;
        }
        if (asset.getVisibility() == AssetVisibility.SHARED && hasGrant(asset.getId(), projectId)) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "资产对该项目不可见: " + asset.getId());
    }

    /** offline/grant 的二次校验：仅资产责任人（owner_user_id）或项目内 ADMIN/OWNER */
    public void requireOwnerOrAdmin(Asset asset, String action) {
        String userId = RequestContext.currentUserId();
        if (userId != null && userId.equals(asset.getOwnerUserId())) {
            return;
        }
        Role role = projectAccessService.roleOf(userId, asset.getOwnerProjectId());
        if (role == Role.OWNER || role == Role.ADMIN) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                "仅资产责任人或项目内 ADMIN/OWNER 可执行 " + action + ": " + asset.getId());
    }

    boolean hasGrant(Long assetId, Long projectId) {
        return assetGrantMapper.selectCount(new LambdaQueryWrapper<AssetGrant>()
                .eq(AssetGrant::getAssetId, assetId)
                .eq(AssetGrant::getToProjectId, projectId)) > 0;
    }

    private void requireMcpServiceParent(Long parentAssetId) {
        Asset parent = getOrThrow(parentAssetId);
        if (parent.getAssetType() != AssetType.MCP_SERVICE) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "MCP_TOOL 的 parent 必须是 MCP_SERVICE: " + parentAssetId);
        }
    }
}
