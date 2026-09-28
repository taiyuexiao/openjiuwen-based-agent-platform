package com.bosc.agentops.assethub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.assethub.dto.AssetGrantReq;
import com.bosc.agentops.assethub.dto.ProjectScopedReq;
import com.bosc.agentops.assethub.entity.Asset;
import com.bosc.agentops.assethub.entity.AssetGrant;
import com.bosc.agentops.assethub.entity.AssetVisibility;
import com.bosc.agentops.assethub.mapper.AssetGrantMapper;
import com.bosc.agentops.common.api.BizException;
import com.bosc.agentops.common.api.ErrorCode;
import com.bosc.agentops.common.audit.AuditService;
import com.bosc.agentops.common.context.RequestContext;
import com.bosc.agentops.project.service.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 跨项目共享授权（grant/revoke/list）。仅资产责任人或项目内 ADMIN/OWNER 可操作（service 层二次校验）。
 * grant 幂等：重复授权返回已有记录；revoke 不影响已有 AssetReference（记录保留，但此后 checkUsable=false）。
 */
@Service
public class AssetGrantService {

    private final AssetGrantMapper assetGrantMapper;
    private final AssetService assetService;
    private final ProjectService projectService;
    private final AuditService auditService;

    public AssetGrantService(AssetGrantMapper assetGrantMapper,
                             AssetService assetService,
                             ProjectService projectService,
                             AuditService auditService) {
        this.assetGrantMapper = assetGrantMapper;
        this.assetService = assetService;
        this.projectService = projectService;
        this.auditService = auditService;
    }

    @Transactional
    public AssetGrant grant(Long assetId, AssetGrantReq req) {
        String userId = RequestContext.currentUserId();
        Asset asset = assetService.getOrThrow(assetId);
        assetService.requireOwnerProject(asset, req.getProjectId());
        assetService.requireOwnerOrAdmin(asset, "grant");
        projectService.getOrThrow(req.getToProjectId());
        if (req.getToProjectId().equals(asset.getOwnerProjectId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "归属项目无需授权: " + req.getToProjectId());
        }
        if (asset.getVisibility() != AssetVisibility.SHARED) {
            throw new BizException(ErrorCode.STATE_CONFLICT,
                    "仅 SHARED 可见性资产可跨项目授权: " + assetId);
        }
        AssetGrant grant = findGrant(assetId, req.getToProjectId());
        if (grant != null) {
            return grant;
        }
        grant = new AssetGrant();
        grant.setAssetId(assetId);
        grant.setToProjectId(req.getToProjectId());
        grant.setGrantedBy(userId);
        assetGrantMapper.insert(grant);
        auditService.record(AssetService.MODULE, "grant.grant", "asset_grant", grant.getId(),
                Map.of("assetId", assetId, "toProjectId", req.getToProjectId()));
        return grant;
    }

    @Transactional
    public void revoke(Long assetId, AssetGrantReq req) {
        Asset asset = assetService.getOrThrow(assetId);
        assetService.requireOwnerProject(asset, req.getProjectId());
        assetService.requireOwnerOrAdmin(asset, "revoke");
        AssetGrant grant = findGrant(assetId, req.getToProjectId());
        if (grant == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "授权不存在: assetId=" + assetId + ", toProjectId=" + req.getToProjectId());
        }
        // revoke 只删除授权记录，已有 AssetReference 保留可查，但此后 checkUsable=false
        assetGrantMapper.deleteById(grant.getId());
        auditService.record(AssetService.MODULE, "grant.revoke", "asset_grant", grant.getId(),
                Map.of("assetId", assetId, "toProjectId", req.getToProjectId()));
    }

    public List<AssetGrant> list(Long assetId, ProjectScopedReq req) {
        Asset asset = assetService.getOrThrow(assetId);
        assetService.requireVisible(asset, req.getProjectId());
        return assetGrantMapper.selectList(new LambdaQueryWrapper<AssetGrant>()
                .eq(AssetGrant::getAssetId, assetId)
                .orderByAsc(AssetGrant::getId));
    }

    private AssetGrant findGrant(Long assetId, Long toProjectId) {
        return assetGrantMapper.selectOne(new LambdaQueryWrapper<AssetGrant>()
                .eq(AssetGrant::getAssetId, assetId)
                .eq(AssetGrant::getToProjectId, toProjectId));
    }
}
