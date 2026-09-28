package com.bosc.agentops.observability.dto;

/**
 * OTLP 接收结果：received/stored/dropped 计数（无法映射 Agent 的 span 丢弃并计数）。
 */
public class OtlpIngestResp {

    private int receivedCount;
    private int storedCount;
    private int droppedCount;

    public OtlpIngestResp() {
    }

    public OtlpIngestResp(int receivedCount, int storedCount, int droppedCount) {
        this.receivedCount = receivedCount;
        this.storedCount = storedCount;
        this.droppedCount = droppedCount;
    }

    public int getReceivedCount() {
        return receivedCount;
    }

    public void setReceivedCount(int receivedCount) {
        this.receivedCount = receivedCount;
    }

    public int getStoredCount() {
        return storedCount;
    }

    public void setStoredCount(int storedCount) {
        this.storedCount = storedCount;
    }

    public int getDroppedCount() {
        return droppedCount;
    }

    public void setDroppedCount(int droppedCount) {
        this.droppedCount = droppedCount;
    }
}
