package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.component.liteflow.registry.ComponentRegistry;
import com.dmp.riskdecision.component.liteflow.service.ComponentCatalogService;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphCompilationException;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.service.impl.FlowComponentValidationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 条件节点不能读取尚未执行的节点结果。 */
class ConditionSourceValidationTest {
    @Test
    void rejectsAConditionReadingItsOwnFutureResult() {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("choose", NodeKind.CONDITION, "equals-value", FailurePolicy.FAIL_FAST,
                        Map.of("sourceNodeId", "future", "path", "businessResult.conclusion", "expected", "OK")),
                new GraphNode("future", NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST,
                        Map.of("value", 1))),
                List.of(new GraphEdge("a", "start", "choose", null),
                        new GraphEdge("b", "choose", "future", "true")), Map.of());
        FlowComponentValidationService validator = new FlowComponentValidationService(
                new ComponentCatalogService(new ComponentRegistry()));

        assertThatThrownBy(() -> validator.requireValid(graph))
                .isInstanceOf(GraphCompilationException.class)
                .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                        ((GraphCompilationException) error).getIssues())
                        .extracting("code").contains("CONDITION_SOURCE_UNAVAILABLE"));
    }
}
