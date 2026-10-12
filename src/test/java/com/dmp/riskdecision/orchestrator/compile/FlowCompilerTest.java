package com.dmp.riskdecision.orchestrator.compile;

import com.dmp.riskdecision.component.liteflow.registry.ComponentRegistry;
import com.dmp.riskdecision.component.liteflow.service.ComponentCatalogService;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 流程图到 LiteFlow EL 的编译行为验证。 */
class FlowCompilerTest {

    private final FlowCompiler compiler = new FlowCompiler(new ComponentCatalogService(new ComponentRegistry()));

    @Test
    void wrapsASingleComponentInAChainExpression() {
        GraphDefinition graph = graph(List.of(node("start", NodeKind.START), component("only"), node("end", NodeKind.END)),
                edge("a", "start", "only"), edge("b", "only", "end"));

        assertThat(compiler.compile("payment", 1, graph).getEl())
                .isEqualTo("THEN(emitValue.tag(\"only\"))");
    }

    @Test
    void compilesSerialAndParallelJoinOnce() {
        CompiledFlow flow = compiler.compile("payment", 3, graph(
                List.of(node("start", NodeKind.START), component("prepare"), node("split", NodeKind.PARALLEL_SPLIT),
                        component("left"), component("right"), node("join", NodeKind.PARALLEL_JOIN),
                        component("decide"), node("end", NodeKind.END)),
                edge("a", "start", "prepare"), edge("b", "prepare", "split"),
                edge("c", "split", "left"), edge("d", "split", "right"),
                edge("e", "left", "join"), edge("f", "right", "join"),
                edge("g", "join", "decide"), edge("h", "decide", "end")));

        assertThat(flow.getChainId()).isEqualTo("scene_payment_v3");
        assertThat(flow.getEl()).contains("WHEN(").contains("emitValue.tag(\"prepare\")");
        assertThat(flow.getEl().split("decide", -1)).hasSize(2);
    }

    @Test
    void compilesConditionWithTrueAndFalseBranches() {
        GraphDefinition graph = graph(List.of(node("start", NodeKind.START),
                        condition("condition"), component("yes"), component("no"),
                        node("join", NodeKind.CONDITION_JOIN), component("after"), node("end", NodeKind.END)),
                edge("a", "start", "condition"), edge("b", "condition", "yes", "true"),
                edge("c", "condition", "no", "false"), edge("d", "yes", "join"),
                edge("e", "no", "join"), edge("f", "join", "after"), edge("g", "after", "end"));

        assertThat(compiler.compile("login", 1, graph).getEl()).contains("IF(").contains("after");
    }

    @Test
    void rejectsSharedNodeBeforeJoin() {
        GraphDefinition graph = graph(List.of(node("start", NodeKind.START), node("split", NodeKind.PARALLEL_SPLIT),
                        component("left"), component("right"), component("shared"),
                        node("join", NodeKind.PARALLEL_JOIN), node("end", NodeKind.END)),
                edge("a", "start", "split"), edge("b", "split", "left"), edge("c", "split", "right"),
                edge("d", "left", "shared"), edge("e", "right", "shared"),
                edge("f", "shared", "join"), edge("g", "join", "end"));

        assertThatThrownBy(() -> compiler.compile("payment", 1, graph))
                .isInstanceOf(GraphCompilationException.class);
    }

    @Test
    void rejectsMismatchedJoinKind() {
        GraphDefinition graph = graph(List.of(node("start", NodeKind.START), node("split", NodeKind.PARALLEL_SPLIT),
                        component("left"), component("right"), node("wrong", NodeKind.CONDITION_JOIN),
                        node("end", NodeKind.END)),
                edge("a", "start", "split"), edge("b", "split", "left"), edge("c", "split", "right"),
                edge("d", "left", "wrong"), edge("e", "right", "wrong"), edge("f", "wrong", "end"));

        assertThatThrownBy(() -> compiler.compile("payment", 1, graph))
                .isInstanceOf(GraphCompilationException.class)
                .hasMessageContaining("join");
    }

    @Test
    void rejectsAnEdgeCrossingParallelBranches() {
        GraphDefinition graph = graph(List.of(node("start", NodeKind.START), node("split", NodeKind.PARALLEL_SPLIT),
                        component("left"), component("right"), node("join", NodeKind.PARALLEL_JOIN),
                        node("end", NodeKind.END)),
                edge("a", "start", "split"), edge("b", "split", "left"), edge("c", "split", "right"),
                edge("d", "left", "join"), edge("e", "right", "join"),
                edge("f", "join", "end"), edge("cross", "left", "right"));

        assertThatThrownBy(() -> compiler.compile("payment", 1, graph))
                .isInstanceOf(GraphCompilationException.class);
    }

    @Test
    void nestsConditionInsideParallelBranch() {
        GraphDefinition graph = graph(List.of(node("start", NodeKind.START), node("split", NodeKind.PARALLEL_SPLIT),
                        condition("condition"), component("yes"), component("no"),
                        node("conditionJoin", NodeKind.CONDITION_JOIN), component("right"),
                        node("parallelJoin", NodeKind.PARALLEL_JOIN), component("after"), node("end", NodeKind.END)),
                edge("a", "start", "split"), edge("b", "split", "condition"), edge("c", "split", "right"),
                edge("d", "condition", "yes", "true"), edge("e", "condition", "no", "false"),
                edge("f", "yes", "conditionJoin"), edge("g", "no", "conditionJoin"),
                edge("h", "conditionJoin", "parallelJoin"), edge("i", "right", "parallelJoin"),
                edge("j", "parallelJoin", "after"), edge("k", "after", "end"));

        assertThat(compiler.compile("payment", 2, graph).getEl()).contains("WHEN(IF(").contains("after");
    }

    private static GraphDefinition graph(List<GraphNode> nodes, GraphEdge... edges) {
        return new GraphDefinition(1, nodes, List.of(edges), Map.of());
    }

    private static GraphNode node(String id, NodeKind kind) {
        return new GraphNode(id, kind, null, null, Map.of());
    }

    private static GraphNode component(String id) {
        return new GraphNode(id, NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST, Map.of());
    }

    private static GraphNode condition(String id) {
        return new GraphNode(id, NodeKind.CONDITION, "is-positive", FailurePolicy.FAIL_FAST, Map.of());
    }

    private static GraphEdge edge(String id, String source, String target) {
        return new GraphEdge(id, source, target, null);
    }

    private static GraphEdge edge(String id, String source, String target, String branch) {
        return new GraphEdge(id, source, target, branch);
    }
}
