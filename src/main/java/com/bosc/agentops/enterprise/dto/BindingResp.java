package com.bosc.agentops.enterprise.dto;

import com.bosc.agentops.enterprise.entity.AppBinding;
import com.bosc.agentops.enterprise.entity.SyncStatus;

import java.time.LocalDateTime;

/**
 * 绑定详情响应：syncStatus 为库内存储值，effectiveSyncStatus 为按阈值动态判定后的有效状态
 * （SYNCED 超阈值未重同步时呈现为 STALE）；warning 仅在重同步未完全成功时返回。
 */
public class BindingResp {

    private Long id;
    private Long agentId;
    private String appCode;
    private String sourceSystem;
    private SyncStatus syncStatus;
    private SyncStatus effectiveSyncStatus;
    private LocalDateTime syncedAt;
    /** 权威信息快照 JSON（appCode/appName/owner/bizDomain/appLevel） */
    private String snapshot;
    private String boundBy;
    private LocalDateTime createdAt;
    private String warning;

    public static BindingResp of(AppBinding binding, SyncStatus effectiveSyncStatus, String warning) {
        BindingResp resp = new BindingResp();
        resp.setId(binding.getId());
        resp.setAgentId(binding.getAgentId());
        resp.setAppCode(binding.getAppCode());
        resp.setSourceSystem(binding.getSourceSystem());
        resp.setSyncStatus(binding.getSyncStatus());
        resp.setEffectiveSyncStatus(effectiveSyncStatus);
        resp.setSyncedAt(binding.getSyncedAt());
        resp.setSnapshot(binding.getSnapshot());
        resp.setBoundBy(binding.getBoundBy());
        resp.setCreatedAt(binding.getCreatedAt());
        resp.setWarning(warning);
        return resp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public SyncStatus getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(SyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    public SyncStatus getEffectiveSyncStatus() {
        return effectiveSyncStatus;
    }

    public void setEffectiveSyncStatus(SyncStatus effectiveSyncStatus) {
        this.effectiveSyncStatus = effectiveSyncStatus;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }

    public String getBoundBy() {
        return boundBy;
    }

    public void setBoundBy(String boundBy) {
        this.boundBy = boundBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }
}
