package com.bosc.agentops.enterprise.gate;

/**
 * 投产前归属校验门禁 SPI：06 模块（制品交付/投产流程）必须调用。
 * 通过条件：存在绑定 且 有效 sync_status=SYNCED（含 STALE 动态判定）且快照含应用分级。
 */
public interface AppBindingGate {

    GateResult validateForRelease(Long agentId);
}
