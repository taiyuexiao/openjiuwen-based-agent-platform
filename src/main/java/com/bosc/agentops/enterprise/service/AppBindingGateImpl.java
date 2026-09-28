package com.bosc.agentops.enterprise.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bosc.agentops.enterprise.config.CmdbProperties;
import com.bosc.agentops.enterprise.entity.AppBinding;
import com.bosc.agentops.enterprise.entity.SyncStatus;
import com.bosc.agentops.enterprise.gate.AppBindingGate;
import com.bosc.agentops.enterprise.gate.GateResult;
import com.bosc.agentops.enterprise.mapper.AppBindingMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 投产前归属校验门禁：06 投产流程必须调用。全部未通过原因聚合返回。
 * 判定基于库内绑定与快照（投产校验用快照说话，避免投产时刻 CMDB 抖动影响发布），
 * STALE 按 synced_at + 阈值动态计算。
 */
@Component
public class AppBindingGateImpl implements AppBindingGate {

    private static final Logger log = LoggerFactory.getLogger(AppBindingGateImpl.class);

    private final AppBindingMapper bindingMapper;
    private final CmdbProperties cmdbProperties;
    private final ObjectMapper objectMapper;

    public AppBindingGateImpl(AppBindingMapper bindingMapper, CmdbProperties cmdbProperties,
                              ObjectMapper objectMapper) {
        this.bindingMapper = bindingMapper;
        this.cmdbProperties = cmdbProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public GateResult validateForRelease(Long agentId) {
        AppBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<AppBinding>()
                .eq(AppBinding::getAgentId, agentId));
        if (binding == null) {
            return GateResult.fail(List.of("Agent 未绑定归属应用，不得进入生产投产流程: agentId=" + agentId));
        }
        List<String> reasons = new ArrayList<>();
        SyncStatus effective = AppBindingService.effectiveStatus(binding, cmdbProperties.getStaleThresholdHours());
        switch (effective) {
            case FAILED -> reasons.add("归属信息同步失败（FAILED）：CMDB 查无应用 " + binding.getAppCode()
                    + "，请核实归属后调用 /bindings/resync 重同步");
            case STALE -> reasons.add("归属信息过期（STALE）：超过 " + cmdbProperties.getStaleThresholdHours()
                    + " 小时未重同步或应用已停用，请先调用 /bindings/resync");
            case UNVERIFIED -> reasons.add("归属信息未完成校验（UNVERIFIED），请先调用 /bindings/resync");
            case SYNCED -> {
            }
        }
        if (!snapshotHasAppLevel(binding)) {
            reasons.add("归属快照缺少应用分级（appLevel），请调用 /bindings/resync 刷新权威信息");
        }
        return reasons.isEmpty() ? GateResult.pass() : GateResult.fail(reasons);
    }

    private boolean snapshotHasAppLevel(AppBinding binding) {
        if (binding.getSnapshot() == null || binding.getSnapshot().isBlank()) {
            return false;
        }
        try {
            JsonNode snapshot = objectMapper.readTree(binding.getSnapshot());
            return snapshot.hasNonNull("appLevel") && !snapshot.get("appLevel").asText().isBlank();
        } catch (Exception e) {
            log.warn("归属快照解析失败, bindingId={}: {}", binding.getId(), e.getMessage());
            return false;
        }
    }
}
