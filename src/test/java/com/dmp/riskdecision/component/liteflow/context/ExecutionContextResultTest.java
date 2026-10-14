package com.dmp.riskdecision.component.liteflow.context;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 单次执行只能固定一个业务结果配置。 */
class ExecutionContextResultTest {
    @Test
    void fixesTheFirstResultAndRejectsAnotherWrite() {
        ExecutionContext context = new ExecutionContext("request", "scene", 1, Map.of(),
                new ConcurrentHashMap<>(), new ConcurrentLinkedQueue<>(), Map.of());
        Map<String, Object> config = new HashMap<>();
        config.put("conclusion", "NO_NEW_OFFER");

        context.setBusinessResultConfig(config);
        config.put("conclusion", "HIGH_VALUE_OFFER");

        assertThat(context.getBusinessResultConfig()).containsEntry("conclusion", "NO_NEW_OFFER");
        assertThatThrownBy(() -> context.setBusinessResultConfig(Map.of("conclusion", "HIGH_VALUE_OFFER")))
                .isInstanceOf(IllegalStateException.class);
    }
}
