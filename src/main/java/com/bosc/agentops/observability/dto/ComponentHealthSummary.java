package com.bosc.agentops.observability.dto;

import java.util.List;

/**
 * 健康聚合：逐个探测 health_endpoint 后的汇总。
 */
public class ComponentHealthSummary {

    private int total;
    private int up;
    private int down;
    private int unknown;
    private List<ComponentHealthItem> components;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getUp() {
        return up;
    }

    public void setUp(int up) {
        this.up = up;
    }

    public int getDown() {
        return down;
    }

    public void setDown(int down) {
        this.down = down;
    }

    public int getUnknown() {
        return unknown;
    }

    public void setUnknown(int unknown) {
        this.unknown = unknown;
    }

    public List<ComponentHealthItem> getComponents() {
        return components;
    }

    public void setComponents(List<ComponentHealthItem> components) {
        this.components = components;
    }
}
