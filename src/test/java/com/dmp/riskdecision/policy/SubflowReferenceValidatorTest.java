package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.repository.FlowAssetRepository;
import com.dmp.riskdecision.policy.service.impl.SubflowReferenceValidator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 子流程引用必须指向可用的当前发布版本。 */
class SubflowReferenceValidatorTest {
    @Test
    void rejectsAnUnpublishedChildFlow() {
        FlowAssetRepository repository = mock(FlowAssetRepository.class);
        when(repository.findPublished("child")).thenReturn(Optional.empty());

        assertThat(new SubflowReferenceValidator(repository)
                .validate("outer", outerGraph("child", 41)).getIssues())
                .extracting("code").contains("SUBFLOW_VERSION_UNAVAILABLE");
    }

    @Test
    void acceptsCurrentPublishedVersionAndRejectsStaleOrNestedVersion() {
        FlowAssetRepository repository = mock(FlowAssetRepository.class);
        FlowVersion published = new FlowVersion(41, "child", 1, childGraph(false),
                "flow_child_v1", "THEN(businessResult.tag(\"result\"))");
        when(repository.findPublished("child")).thenReturn(Optional.of(published));
        SubflowReferenceValidator validator = new SubflowReferenceValidator(repository);

        assertThat(validator.validate("outer", outerGraph("child", 41)).isValid()).isTrue();
        assertThat(validator.validate("outer", outerGraph("child", 40)).getIssues())
                .extracting("code").contains("SUBFLOW_VERSION_UNAVAILABLE");
        assertThat(validator.validate("child", outerGraph("child", 41)).getIssues())
                .extracting("code").contains("SUBFLOW_CYCLE");
        GraphDefinition fractional = outerGraph("child", 41);
        fractional.getNodes().stream().filter(node -> node.getKind() == NodeKind.SUBFLOW)
                .findFirst().orElseThrow().setConfig(Map.of("flowCode", "child", "versionId", 41.5,
                        "inputMappings", Map.of()));
        assertThat(validator.validate("outer", fractional).getIssues())
                .extracting("code").contains("SUBFLOW_CONFIG_INVALID");

        FlowVersion nested = new FlowVersion(42, "child", 2, childGraph(true),
                "flow_child_v2", "THEN(decisionSubflow.tag(\"nested\"))");
        when(repository.findPublished("child")).thenReturn(Optional.of(nested));
        assertThat(validator.validate("outer", outerGraph("child", 42)).getIssues())
                .extracting("code").contains("SUBFLOW_NESTING_UNSUPPORTED");
    }

    @Test
    void rejectsMissingMappingForDeclaredInput() {
        FlowAssetRepository repository = mock(FlowAssetRepository.class);
        GraphDefinition child = childGraph(false);
        child.setRequiredInputs(List.of("userId"));
        FlowVersion published = new FlowVersion(41, "child", 1, child,
                "flow_child_v1", "THEN(businessResult.tag(\"result\"))");
        when(repository.findPublished("child")).thenReturn(Optional.of(published));
        SubflowReferenceValidator validator = new SubflowReferenceValidator(repository);

        assertThat(validator.validate("outer", outerGraph("child", 41)).getIssues())
                .extracting("code").contains("SUBFLOW_INPUT_MAPPING_INVALID");
    }

    private static GraphDefinition outerGraph(String childCode, long versionId) {
        return new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("call", NodeKind.SUBFLOW, null, FailurePolicy.FAIL_FAST,
                        Map.of("flowCode", childCode, "versionId", versionId, "inputMappings", Map.of())),
                new GraphNode("result", NodeKind.RESULT, null, null,
                        Map.of("conclusion", "DONE", "reasonCode", "DONE", "reason", "完成")),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "call", null),
                        new GraphEdge("b", "call", "result", null),
                        new GraphEdge("c", "result", "end", null)), Map.of());
    }

    private static GraphDefinition childGraph(boolean nested) {
        List<GraphNode> nodes = nested ? List.of(
                new GraphNode("nested", NodeKind.SUBFLOW, null, FailurePolicy.FAIL_FAST, Map.of()))
                : List.of(new GraphNode("result", NodeKind.RESULT, null, null,
                Map.of("conclusion", "OK", "reasonCode", "OK", "reason", "通过")));
        return new GraphDefinition(1, nodes, List.of(), Map.of());
    }
}
