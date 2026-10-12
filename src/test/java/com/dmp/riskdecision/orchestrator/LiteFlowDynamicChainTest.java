package com.dmp.riskdecision.orchestrator;

import com.dmp.riskdecision.component.ExecutionContext;
import com.yomahub.liteflow.core.FlowExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LiteFlowDynamicChainTest {
    @Autowired LiteFlowChainRegistry registry;
    @Autowired FlowExecutor executor;

    @Test
    void bindsRepeatedComponentInstancesAndConditionBranch() {
        String chainId = "dynamic_binding_test";
        registry.load(chainId,
                "THEN(emitValue.tag(\"first\"),emitValue.tag(\"second\"),"
                        + "IF(isPositive.tag(\"condition\"),emitValue.tag(\"yes\"),emitValue.tag(\"no\")))");
        ExecutionContext context = new ExecutionContext("req-1", "test", 1,
                Map.of("positive", true), new ConcurrentHashMap<>(), new ConcurrentLinkedQueue<>(),
                Map.of("first", Map.of("value", 1), "second", Map.of("value", 2),
                        "yes", Map.of("value", "Y"), "no", Map.of("value", "N"),
                        "condition", Map.of("inputKey", "positive")));

        var response = executor.execute2Resp(chainId, null, context);

        assertThat(response.isSuccess()).isTrue();
        assertThat(context.nodeResults()).containsEntry("first", 1).containsEntry("second", 2)
                .containsEntry("yes", "Y").doesNotContainKey("no");
    }
}
