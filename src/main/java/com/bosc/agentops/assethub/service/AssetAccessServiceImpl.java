package com.bosc.agentops.assethub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.assethub.entity.Asset;
import com.bosc.agentops.assethub.entity.AssetGrant;
import com.bosc.agentops.assethub.entity.AssetReference;
import com.bosc.agentops.assethub.entity.AssetStatus;
import com.bosc.agentops.assethub.entity.AssetVersion;
import com.bosc.agentops.assethub.entity.AssetVersionStatus;
import com.bosc.agentops.assethub.entity.AssetVisibility;
import com.bosc.agentops.assethub.mapper.AssetGrantMapper;
import com.bosc.agentops.assethub.mapper.AssetMapper;
import com.bosc.agentops.assethub.mapper.AssetReferenceMapper;
import com.bosc.agentops.assethub.mapper.AssetVersionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AssetAccessService 实现。判定规则见接口注释；归属项目始终可用（资产与版本均 PUBLISHED 时）。
 */
@Service
public class AssetAccessServiceImpl implements AssetAccessService {

    private final AssetMapper assetMapper;
    private final AssetVersionMapper assetVersionMapper;
    private final AssetGrantMapper assetGrantMapper;
    private final AssetReferenceMapper assetReferenceMapper;

    public AssetAccessServiceImpl(AssetMapper assetMapper,
                                  AssetVersionMapper assetVersionMapper,
                                  AssetGrantMapper assetGrantMapper,
                                  AssetReferenceMapper assetReferenceMapper) {
        this.assetMapper = assetMapper;
        this.assetVersionMapper = assetVersionMapper;
        this.assetGrantMapper = assetGrantMapper;
        this.assetReferenceMapper = assetReferenceMapper;
    }

    @Override
    public boolean checkUsable(Long assetId, String version, Long projectId) {
        if (assetId == null || version == null || projectId == null) {
            return false;
        }
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.getStatus() != AssetStatus.PUBLISHED) {
            return false;
        }
        AssetVersion assetVersion = assetVersionMapper.selectOne(new LambdaQueryWrapper<AssetVersion>()
                .eq(AssetVersion::getAssetId, assetId)
                .eq(AssetVersion::getVersion, version));
        if (assetVersion == null || assetVersion.getStatus() != AssetVersionStatus.PUBLISHED) {
            return false;
        }
        if (asset.getOwnerProjectId().equals(projectId)) {
            return true;
        }
        if (asset.getVisibility() != AssetVisibility.SHARED) {
            return false;
        }
        return assetGrantMapper.selectCount(new LambdaQueryWrapper<AssetGrant>()
                .eq(AssetGrant::getAssetId, assetId)
                .eq(AssetGrant::getToProjectId, projectId)) > 0;
    }

    @Override
    public List<AssetReference> listReferences(Long assetId) {
        return assetReferenceMapper.selectList(new LambdaQueryWrapper<AssetReference>()
                .eq(AssetReference::getAssetId, assetId)
                .orderByAsc(AssetReference::getId));
    }
}
