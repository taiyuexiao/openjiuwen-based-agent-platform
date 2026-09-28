package com.bosc.agentops.delivery;

import com.bosc.agentops.delivery.config.DeliveryProperties;
import com.bosc.agentops.delivery.executor.OpenJiuwenRuntimeExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenJiuwenRuntimeExecutor 骨架：未配置远端地址时构造即报明确错误（fail-fast，不静默回退）。
 */
class OpenJiuwenRuntimeExecutorTest {

    @Test
    void missingRuntimeBaseUrlFailsFast() {
        DeliveryProperties properties = new DeliveryProperties();
        assertThatThrownBy(() -> new OpenJiuwenRuntimeExecutor(properties, new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agentops.delivery.openjiuwen.runtime-base-url");
    }
}
