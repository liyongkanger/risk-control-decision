package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.component.liteflow.registry.ComponentRegistry;
import com.dmp.riskdecision.component.liteflow.service.ComponentCatalogService;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.FlowContextBinding;
import com.dmp.riskdecision.orchestrator.graph.GraphCompilationException;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.service.impl.FlowComponentValidationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 流程上下文变量在单条流程中只能绑定一个生产节点。 */
class FlowContextBindingValidationTest {
    @Test
    void rejectsTwoProducersForOneVariable() {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("first", NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST,
                        Map.of("value", 1)),
                new GraphNode("second", NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST,
                        Map.of("value", 2))),
                List.of(new GraphEdge("a", "start", "first", null),
                        new GraphEdge("b", "first", "second", null)), Map.of());
        graph.setContextBindings(List.of(
                new FlowContextBinding("score", 7, "first", "value"),
                new FlowContextBinding("score", 7, "second", "value")));
        VariableService variables = mock(VariableService.class);
        VariableVersion version = mock(VariableVersion.class);
        when(variables.current("score")).thenReturn(version);
        when(version.getId()).thenReturn(7L);
        when(version.getSourceType()).thenReturn("FLOW_CONTEXT");
        FlowComponentValidationService validator = new FlowComponentValidationService(
                new ComponentCatalogService(new ComponentRegistry()));
        ReflectionTestUtils.setField(validator, "variables", variables);

        assertThatThrownBy(() -> validator.requireValid(graph))
                .isInstanceOf(GraphCompilationException.class)
                .satisfies(error -> assertThat(((GraphCompilationException) error).getIssues())
                        .extracting("code").contains("VARIABLE_CONFIG_INVALID"));
    }

    @Test
    void rejectsAFieldAbsentFromAStaticProducerResult() {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("producer", NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST,
                        Map.of("value", Map.of("score", 80))),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "producer", null),
                        new GraphEdge("b", "producer", "end", null)), Map.of());
        graph.setContextBindings(List.of(new FlowContextBinding("riskScore", 8, "producer", "missingField")));
        VariableService variables = mock(VariableService.class);
        VariableVersion version = mock(VariableVersion.class);
        when(variables.current("riskScore")).thenReturn(version);
        when(version.getId()).thenReturn(8L);
        when(version.getSourceType()).thenReturn("FLOW_CONTEXT");
        when(version.getValueType()).thenReturn(VariableValueType.DECIMAL);
        FlowComponentValidationService validator = new FlowComponentValidationService(
                new ComponentCatalogService(new ComponentRegistry()));
        ReflectionTestUtils.setField(validator, "variables", variables);

        assertThatThrownBy(() -> validator.requireValid(graph))
                .isInstanceOf(GraphCompilationException.class)
                .satisfies(error -> assertThat(((GraphCompilationException) error).getIssues())
                        .extracting("code").contains("VARIABLE_CONFIG_INVALID"));
    }

    @Test
    void rejectsAConditionThatDoesNotStoreANodeResult() {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("condition", NodeKind.CONDITION, "is-positive", FailurePolicy.FAIL_FAST,
                        Map.of("inputKey", "positive")),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "condition", null),
                        new GraphEdge("b", "condition", "end", "true")), Map.of());
        graph.setContextBindings(List.of(new FlowContextBinding("conditionValue", 9, "condition", "")));
        VariableService variables = mock(VariableService.class);
        VariableVersion version = mock(VariableVersion.class);
        when(variables.current("conditionValue")).thenReturn(version);
        when(version.getId()).thenReturn(9L);
        when(version.getSourceType()).thenReturn("FLOW_CONTEXT");
        when(version.getValueType()).thenReturn(VariableValueType.BOOLEAN);
        FlowComponentValidationService validator = new FlowComponentValidationService(
                new ComponentCatalogService(new ComponentRegistry()));
        ReflectionTestUtils.setField(validator, "variables", variables);

        assertThatThrownBy(() -> validator.requireValid(graph))
                .isInstanceOf(GraphCompilationException.class);
    }
}
