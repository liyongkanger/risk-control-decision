package com.dmp.riskdecision.orchestrator;

import com.dmp.riskdecision.component.liteflow.context.ExecutionContext;
import com.dmp.riskdecision.component.liteflow.registry.LiteFlowChainRegistry;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** LiteFlow 动态链加载与执行验证。 */
@SpringBootTest
class LiteFlowDynamicChainTest {
    @Resource
    private LiteFlowChainRegistry registry;
    @Resource
    private FlowExecutor executor;

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

        LiteflowResponse response = executor.execute2Resp(chainId, null, context);

        assertThat(response.isSuccess()).isTrue();
        assertThat(context.getNodeResults()).containsEntry("first", 1).containsEntry("second", 2)
                .containsEntry("yes", "Y").doesNotContainKey("no");

        ExecutionContext negative = new ExecutionContext("req-2", "test", 1,
                Map.of("positive", false), new ConcurrentHashMap<>(), new ConcurrentLinkedQueue<>(),
                context.getNodeConfigs());
        LiteflowResponse negativeResponse = executor.execute2Resp(chainId, null, negative);
        assertThat(negativeResponse.isSuccess()).isTrue();
        assertThat(negative.getNodeResults()).containsEntry("no", "N").doesNotContainKey("yes");

        assertThatThrownBy(() -> registry.load(chainId, "THEN(emitValue.tag(\"changed\"))"))
                .isInstanceOf(IllegalStateException.class);
    }
}
