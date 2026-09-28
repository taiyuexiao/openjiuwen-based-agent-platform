package com.bosc.agentops.enterprise.gate;

import java.util.List;

/**
 * 门禁结果：passed + 全部未通过原因（聚合返回，不止第一条）。
 */
public class GateResult {

    private final boolean passed;
    private final List<String> reasons;

    private GateResult(boolean passed, List<String> reasons) {
        this.passed = passed;
        this.reasons = reasons;
    }

    public static GateResult pass() {
        return new GateResult(true, List.of());
    }

    public static GateResult fail(List<String> reasons) {
        return new GateResult(false, List.copyOf(reasons));
    }

    public boolean isPassed() {
        return passed;
    }

    public List<String> getReasons() {
        return reasons;
    }
}
