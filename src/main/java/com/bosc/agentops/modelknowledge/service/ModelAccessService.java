package com.bosc.agentops.modelknowledge.service;

import com.bosc.agentops.modelknowledge.dto.EffectiveModel;

import java.util.List;
import java.util.Map;

/**
 * 模型访问 SPI，供其他模块（02 Agent 声明登记、底座/SDK 配置拉取）依赖。
 */
public interface ModelAccessService {

    /**
     * 项目当前可用模型 + 参数策略合并结果。
     * 只返回目录项（Provider 与 ModelService）均为 ENABLED 的授权；disable 后不再下发，历史授权记录保留。
     */
    List<EffectiveModel> effectiveModels(Long projectId);

    /** 项目是否被授权使用某模型（目录项 ENABLED 且授权存在） */
    boolean checkModelAllowed(Long projectId, String modelCode);

    /**
     * 声明参数策略校验（供 02 声明登记）：以声明参数为输入、项目 param_policy 为白名单做合并，
     * 返回被策略剔除的参数键（removedKeys）；空 = 全部允许。policy 为空时不作限制。
     * 模型未授权/已停用时返回全部声明键（调用方应先经 checkModelAllowed 判定）。
     */
    List<String> removedParamKeys(Long projectId, String modelCode, Map<String, Object> declaredParams);
}
