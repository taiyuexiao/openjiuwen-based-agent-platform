package com.bosc.agentops.observability.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * 行内告警平台 webhook 推送体（机器通道，X-Platform-Token 校验）。
 */
public class AlertWebhookReq {

    /** 行内告警平台标识 */
    private String source;

    private String alertKey;

    private Long agentId;

    private String level;

    @NotBlank
    private String title;

    /** 任意 JSON，落库前脱敏 */
    private JsonNode detail;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAlertKey() {
        return alertKey;
    }

    public void setAlertKey(String alertKey) {
        this.alertKey = alertKey;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public JsonNode getDetail() {
        return detail;
    }

    public void setDetail(JsonNode detail) {
        this.detail = detail;
    }
}
