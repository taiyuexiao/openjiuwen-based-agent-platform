package com.bosc.agentops.assethub.service;

import com.bosc.agentops.assethub.entity.AssetReference;

import java.util.List;

/**
 * 资产访问 SPI，供其他模块依赖（02 声明登记、07 运行治理）。
 */
public interface AssetAccessService {

    /**
     * 可见性+授权+状态判定：资产 PUBLISHED 且版本 PUBLISHED，且
     * （归属该项目，或 visibility=SHARED 且存在该项目 AssetGrant）。OFFLINE / 未授权 / 版本不存在均为 false。
     */
    boolean checkUsable(Long assetId, String version, Long projectId);

    /** 资产的全部引用记录（下线前影响分析）；OFFLINE 后已有引用保留可查 */
    List<AssetReference> listReferences(Long assetId);
}
