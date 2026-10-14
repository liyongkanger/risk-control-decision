package com.dmp.riskdecision.orchestrator.graph;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 并行分支都会执行，条件分支只执行命中侧。 */
class GraphSourceAvailabilityTest {
    @Test
    void makesParallelBranchResultsAvailableAfterJoin() {
        GraphDefinition graph = graph(NodeKind.PARALLEL_SPLIT, NodeKind.PARALLEL_JOIN,
                null, null);

        assertThat(GraphSourceAvailability.isAvailable(graph, "left", "after")).isTrue();
    }

    @Test
    void doesNotPromiseOneConditionalBranchResultAfterJoin() {
        GraphDefinition graph = graph(NodeKind.CONDITION, NodeKind.CONDITION_JOIN,
                "true", "false");

        assertThat(GraphSourceAvailability.isAvailable(graph, "left", "after")).isFalse();
    }

    private static GraphDefinition graph(NodeKind splitKind, NodeKind joinKind,
                                         String leftBranch, String rightBranch) {
        return new GraphDefinition(1, List.of(
                node("start", NodeKind.START), node("split", splitKind),
                node("left", NodeKind.COMPONENT), node("right", NodeKind.COMPONENT),
                node("join", joinKind), node("after", NodeKind.SUBFLOW), node("end", NodeKind.END)),
                List.of(edge("a", "start", "split", null),
                        edge("b", "split", "left", leftBranch),
                        edge("c", "split", "right", rightBranch),
                        edge("d", "left", "join", null),
                        edge("e", "right", "join", null),
                        edge("f", "join", "after", null),
                        edge("g", "after", "end", null)), Map.of());
    }

    private static GraphNode node(String id, NodeKind kind) {
        return new GraphNode(id, kind, null, null, Map.of());
    }

    private static GraphEdge edge(String id, String source, String target, String branch) {
        return new GraphEdge(id, source, target, branch);
    }
}
