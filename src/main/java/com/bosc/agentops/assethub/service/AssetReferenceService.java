package com.bosc.agentops.assethub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.assethub.dto.ProjectScopedReq;
import com.bosc.agentops.assethub.dto.ReferenceAddReq;
import com.bosc.agentops.assethub.dto.ReferenceRemoveReq;
import com.bosc.agentops.assethub.entity.Asset;
import com.bosc.agentops.assethub.entity.AssetReference;
import com.bosc.agentops.assethub.mapper.AssetReferenceMapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.project.entity.Project;
import com.bosc.agentops.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 资产引用登记（add/remove/list）。add 前置校验 checkUsable；
 * 同一 (asset, version, project, agent) 重复引用幂等返回已有记录。
 */
@Service
public class AssetReferenceService {

    private final AssetReferenceMapper assetReferenceMapper;
    private final AssetAccessService assetAccessService;
    private final AssetService assetService;
    private final ProjectService projectService;
    private final AuditService auditService;

    public AssetReferenceService(AssetReferenceMapper assetReferenceMapper,
                                 AssetAccessService assetAccessService,
                                 AssetService assetService,
                                 ProjectService projectService,
                                 AuditService auditService) {
        this.assetReferenceMapper = assetReferenceMapper;
        this.assetAccessService = assetAccessService;
        this.assetService = assetService;
        this.projectService = projectService;
        this.auditService = auditService;
    }

    @Transactional
    public AssetReference add(Long assetId, ReferenceAddReq req) {
        String userId = RequestContext.currentUserId();
        Project project = projectService.getOrThrow(req.getProjectId());
        projectService.requireActive(project);
        if (!assetAccessService.checkUsable(assetId, req.getAssetVersion(), req.getProjectId())) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "资产不可用（未发布/未授权/已下线），禁止登记引用: assetId=" + assetId);
        }
        AssetReference existing = findReference(assetId, req.getAssetVersion(), req.getProjectId(), req.getAgentId());
        if (existing != null) {
            return existing;
        }
        AssetReference reference = new AssetReference();
        reference.setAssetId(assetId);
        reference.setAssetVersion(req.getAssetVersion());
        reference.setProjectId(req.getProjectId());
        reference.setAgentId(req.getAgentId());
        reference.setCreatedBy(userId);
        assetReferenceMapper.insert(reference);
        auditService.record(AssetService.MODULE, "reference.add", "asset_reference", reference.getId(),
                Map.of("assetId", assetId, "assetVersion", req.getAssetVersion(),
                        "projectId", req.getProjectId()));
        return reference;
    }

    @Transactional
    public void remove(Long assetId, ReferenceRemoveReq req) {
        AssetReference reference = assetReferenceMapper.selectById(req.getRefId());
        if (reference == null || !reference.getAssetId().equals(assetId)
                || !reference.getProjectId().equals(req.getProjectId())) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "引用不存在: assetId=" + assetId + ", refId=" + req.getRefId());
        }
        assetReferenceMapper.deleteById(reference.getId());
        auditService.record(AssetService.MODULE, "reference.remove", "asset_reference", reference.getId(),
                Map.of("assetId", assetId, "projectId", req.getProjectId()));
    }

    /** 归属项目可查全部引用（下线影响分析）；其他项目仅可查自己的引用 */
    public List<AssetReference> list(Long assetId, ProjectScopedReq req) {
        Asset asset = assetService.getOrThrow(assetId);
        assetService.requireVisible(asset, req.getProjectId());
        LambdaQueryWrapper<AssetReference> wrapper = new LambdaQueryWrapper<AssetReference>()
                .eq(AssetReference::getAssetId, assetId)
                .eq(!asset.getOwnerProjectId().equals(req.getProjectId()),
                        AssetReference::getProjectId, req.getProjectId())
                .orderByAsc(AssetReference::getId);
        return assetReferenceMapper.selectList(wrapper);
    }

    private AssetReference findReference(Long assetId, String assetVersion, Long projectId, Long agentId) {
        return assetReferenceMapper.selectOne(new LambdaQueryWrapper<AssetReference>()
                .eq(AssetReference::getAssetId, assetId)
                .eq(AssetReference::getAssetVersion, assetVersion)
                .eq(AssetReference::getProjectId, projectId)
                .eq(agentId != null, AssetReference::getAgentId, agentId)
                .isNull(agentId == null, AssetReference::getAgentId));
    }
}
