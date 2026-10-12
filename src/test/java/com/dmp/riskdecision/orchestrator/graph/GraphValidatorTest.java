package com.dmp.riskdecision.orchestrator.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphValidatorTest {

    private final GraphValidator validator = new GraphValidator();

    @Test
    void acceptsACompleteSerialGraph() {
        assertThat(validator.validateForPublish(serialGraph()).valid()).isTrue();
    }

    @Test
    void identifiesDuplicateIdsAndMissingEndpoints() {
        GraphDefinition base = serialGraph();
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        nodes.add(component("work"));
        List<GraphEdge> edges = new ArrayList<>(base.edges());
        edges.add(new GraphEdge("dangling", "work", "absent", null));

        assertThat(validator.validateForPublish(new GraphDefinition(1, nodes, edges, Map.of())).issues())
                .extracting(GraphIssue::code)
                .contains("DUPLICATE_NODE_ID", "EDGE_TARGET_MISSING");
    }

    @Test
    void rejectsCycleAndUnreachableNode() {
        GraphDefinition base = serialGraph();
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        nodes.add(component("isolated"));
        List<GraphEdge> edges = new ArrayList<>(base.edges());
        edges.add(new GraphEdge("cycle", "work", "work", null));

        assertThat(validator.validateForPublish(new GraphDefinition(1, nodes, edges, Map.of())).issues())
                .extracting(GraphIssue::code)
                .contains("GRAPH_CYCLE", "NODE_UNREACHABLE");
    }

    @Test
    void checksComponentDegreeAndFailurePolicy() {
        GraphDefinition base = serialGraph();
        List<GraphNode> nodes = List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("work", NodeKind.COMPONENT, "emit-value", null, Map.of()),
                new GraphNode("end", NodeKind.END, null, null, Map.of()));
        List<GraphEdge> edges = List.of(new GraphEdge("a", "start", "work", null));

        assertThat(validator.validateForPublish(new GraphDefinition(1, nodes, edges, base.layout())).issues())
                .extracting(GraphIssue::code)
                .contains("INVALID_DEGREE", "FAILURE_POLICY_REQUIRED");
    }

    @Test
    void acceptsAnIncompleteDraftButCannotPublishIt() {
        GraphDefinition draft = new GraphDefinition(1,
                List.of(new GraphNode("start", NodeKind.START, null, null, Map.of())),
                List.of(), Map.of());

        assertThat(validator.validateDraft(draft).valid()).isTrue();
        assertThat(validator.validateForPublish(draft).issues())
                .extracting(GraphIssue::code).contains("END_COUNT");
    }

    @Test
    void requiresConditionBranchLabelsAndJoinDegree() {
        GraphDefinition graph = new GraphDefinition(1,
                List.of(new GraphNode("start", NodeKind.START, null, null, Map.of()),
                        new GraphNode("condition", NodeKind.CONDITION, "is-positive", null, Map.of()),
                        component("yes"), component("no"),
                        new GraphNode("join", NodeKind.CONDITION_JOIN, null, null, Map.of()),
                        new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "condition", null),
                        new GraphEdge("b", "condition", "yes", "true"),
                        new GraphEdge("c", "condition", "no", "true"),
                        new GraphEdge("d", "yes", "join", null),
                        new GraphEdge("e", "no", "join", null),
                        new GraphEdge("f", "join", "end", null)), Map.of());

        assertThat(validator.validateForPublish(graph).issues())
                .extracting(GraphIssue::code).contains("CONDITION_BRANCHES", "FAILURE_POLICY_REQUIRED");
    }

    @Test
    void capsSerializedDraftSizeEvenWithFewNodes() {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("work", NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST,
                        Map.of("value", "x".repeat(1_100_000))),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "work", null),
                        new GraphEdge("b", "work", "end", null)), Map.of());

        assertThat(validator.validateDraft(graph).issues())
                .extracting(GraphIssue::code).contains("GRAPH_TOO_LARGE");
    }

    static GraphDefinition serialGraph() {
        return new GraphDefinition(1,
                List.of(new GraphNode("start", NodeKind.START, null, null, Map.of()),
                        component("work"),
                        new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "work", null),
                        new GraphEdge("b", "work", "end", null)), Map.of());
    }

    static GraphNode component(String id) {
        return new GraphNode(id, NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST, Map.of());
    }
}
