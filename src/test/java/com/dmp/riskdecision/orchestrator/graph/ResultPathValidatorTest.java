package com.dmp.riskdecision.orchestrator.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 显式业务结果的配置与路径覆盖校验。 */
class ResultPathValidatorTest {
    private final ResultPathValidator validator = new ResultPathValidator();

    @Test
    void acceptsOneResultOnTheOnlyPath() {
        assertThat(validator.validate(serial(result("outcome"))).isValid()).isTrue();
    }

    @Test
    void reportsMissingAndDuplicateResultsAtTheRelevantNode() {
        assertThat(validator.validate(serial(component("work"))).getIssues())
                .extracting(GraphIssue::getCode).contains("RESULT_MISSING");
        GraphDefinition duplicate = new GraphDefinition(1,
                List.of(start(), result("first"), result("second"), end()),
                List.of(edge("a", "start", "first"), edge("b", "first", "second"),
                        edge("c", "second", "end")), Map.of());
        assertThat(validator.validate(duplicate).getIssues())
                .anySatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo("RESULT_DUPLICATE");
                    assertThat(issue.getNodeId()).isEqualTo("second");
                });
    }

    @Test
    void requiresAResultOnBothConditionalBranches() {
        GraphDefinition complete = conditionGraph(true);
        GraphDefinition missing = conditionGraph(false);

        assertThat(validator.validate(complete).isValid()).isTrue();
        assertThat(validator.validate(missing).getIssues())
                .extracting(GraphIssue::getCode).contains("RESULT_MISSING");
    }

    @Test
    void rejectsAResultInsideAParallelBranch() {
        GraphDefinition graph = new GraphDefinition(1,
                List.of(start(), component("prepare"), split(), result("parallelResult"),
                        component("other"), parallelJoin(), result("after"), end()),
                List.of(edge("a", "start", "prepare"), edge("b", "prepare", "split"),
                        edge("c", "split", "parallelResult"), edge("d", "split", "other"),
                        edge("e", "parallelResult", "join"), edge("f", "other", "join"),
                        edge("g", "join", "after"), edge("h", "after", "end")), Map.of());

        assertThat(validator.validate(graph).getIssues())
                .anySatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo("RESULT_PARALLEL_UNSUPPORTED");
                    assertThat(issue.getNodeId()).isEqualTo("parallelResult");
                });
    }

    @Test
    void rejectsParallelSplitAfterResult() {
        GraphDefinition graph = new GraphDefinition(1,
                List.of(start(), result("early"), split(), component("left"), component("right"),
                        parallelJoin(), component("after"), end()),
                List.of(edge("a", "start", "early"), edge("b", "early", "split"),
                        edge("c", "split", "left"), edge("d", "split", "right"),
                        edge("e", "left", "join"), edge("f", "right", "join"),
                        edge("g", "join", "after"), edge("h", "after", "end")), Map.of());

        assertThat(validator.validate(graph).getIssues())
                .extracting(GraphIssue::getCode).contains("RESULT_PARALLEL_UNSUPPORTED");
    }

    @Test
    void rejectsMissingAndUnknownConfigurationFields() {
        GraphNode invalid = new GraphNode("outcome", NodeKind.RESULT, null, null,
                Map.of("conclusion", " ", "reasonCode", "OK", "reason", "说明", "unknown", 1));

        assertThat(validator.validate(serial(invalid)).getIssues())
                .anySatisfy(issue -> {
                    assertThat(issue.getCode()).isEqualTo("RESULT_CONFIG_INVALID");
                    assertThat(issue.getNodeId()).isEqualTo("outcome");
                });
    }

    @Test
    void incompleteDraftStillPassesTheBaseDraftCheck() {
        assertThat(new GraphValidator().validateDraft(serial(component("work"))).isValid()).isTrue();
    }

    private static GraphDefinition serial(GraphNode middle) {
        return new GraphDefinition(1, List.of(start(), middle, end()),
                List.of(edge("a", "start", middle.getId()), edge("b", middle.getId(), "end")), Map.of());
    }

    private static GraphDefinition conditionGraph(boolean bothResults) {
        List<GraphNode> nodes = new ArrayList<>(List.of(start(), condition(),
                result("yes"), bothResults ? result("no") : component("no"),
                conditionJoin(), component("after"), end()));
        return new GraphDefinition(1, nodes,
                List.of(edge("a", "start", "condition"),
                        new GraphEdge("b", "condition", "yes", "true"),
                        new GraphEdge("c", "condition", "no", "false"),
                        edge("d", "yes", "conditionJoin"), edge("e", "no", "conditionJoin"),
                        edge("f", "conditionJoin", "after"), edge("g", "after", "end")), Map.of());
    }

    private static GraphNode start() { return new GraphNode("start", NodeKind.START, null, null, Map.of()); }
    private static GraphNode end() { return new GraphNode("end", NodeKind.END, null, null, Map.of()); }
    private static GraphNode split() { return new GraphNode("split", NodeKind.PARALLEL_SPLIT, null, null, Map.of()); }
    private static GraphNode parallelJoin() { return new GraphNode("join", NodeKind.PARALLEL_JOIN, null, null, Map.of()); }
    private static GraphNode conditionJoin() { return new GraphNode("conditionJoin", NodeKind.CONDITION_JOIN, null, null, Map.of()); }
    private static GraphNode condition() { return new GraphNode("condition", NodeKind.CONDITION, "is-positive", FailurePolicy.FAIL_FAST, Map.of("inputKey", "positive")); }
    private static GraphNode component(String id) { return new GraphNode(id, NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST, Map.of("value", 1)); }
    private static GraphNode result(String id) { return new GraphNode(id, NodeKind.RESULT, null, null, Map.of("conclusion", "NO_NEW_OFFER", "reasonCode", "NONE", "reason", "无新增优惠")); }
    private static GraphEdge edge(String id, String source, String target) { return new GraphEdge(id, source, target, null); }
}
