package com.dmp.riskdecision.component.liteflow.compile;

import com.dmp.riskdecision.component.liteflow.service.ComponentCatalogService;
import com.dmp.riskdecision.component.liteflow.service.CompiledFlow;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphCompilationException;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.GraphValidationResult;
import com.dmp.riskdecision.orchestrator.graph.GraphValidator;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import jakarta.annotation.Resource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 将合法流程图编译为 LiteFlow EL 表达式。 */
@org.springframework.stereotype.Component
public class FlowCompiler {
    private final GraphValidator validator = new GraphValidator();
    @Resource
    private ComponentCatalogService components;

    public FlowCompiler(ComponentCatalogService components) {
        this.components = components;
    }

    public FlowCompiler() {
    }

    /**
     * 编译独立流程图，并保留节点与 LiteFlow 标签的绑定关系。
     *
     * <p>先通过 {@link GraphValidator#validateForPublish(GraphDefinition)} 校验图结构，
     * 再由内部解析器生成 EL；不支持的分支结构抛出 {@link GraphCompilationException}。</p>
     */
    public CompiledFlow compile(String flowCode, long version, GraphDefinition graph) {
        if (flowCode == null || !flowCode.matches("[A-Za-z][A-Za-z0-9_-]{0,63}") || version < 1) {
            throw new IllegalArgumentException("Invalid flow code or version");
        }
        GraphValidationResult validation = validator.validateForPublish(graph);
        if (!validation.isValid()) throw new GraphCompilationException(validation.getIssues());
        Parser parser = new Parser(graph, components);
        String start = graph.getNodes().stream().filter(n -> n.getKind() == NodeKind.START).findFirst().orElseThrow().getId();
        String end = graph.getNodes().stream().filter(n -> n.getKind() == NodeKind.END).findFirst().orElseThrow().getId();
        String el = parser.sequence(parser.onlyNext(start), end);
        if (!el.startsWith("THEN(") && !el.startsWith("WHEN(") && !el.startsWith("IF(")) {
            el = "THEN(" + el + ")";
        }
        if (parser.seen.size() != graph.getNodes().stream().filter(n -> n.getKind() == NodeKind.COMPONENT
                || n.getKind() == NodeKind.CONDITION).count()) {
            throw problem("UNCONSUMED_NODE", null, "Graph contains nodes outside the compiled structure");
        }
        Map<String, String> bindings = new HashMap<>();
        for (GraphNode node : graph.getNodes()) {
            if (node.getKind() == NodeKind.COMPONENT || node.getKind() == NodeKind.CONDITION) {
                bindings.put(node.getId(), node.getId());
            }
        }
        return new CompiledFlow("flow_" + flowCode + "_v" + version, el, Map.copyOf(bindings));
    }

    private static GraphCompilationException problem(String code, String nodeId, String message) {
        return new GraphCompilationException(List.of(new GraphIssue(code, nodeId, null, message)));
    }

    /** 将单入口图区域转换为顺序、并行和条件表达式。 */
    private static final class Parser {
        private final Map<String, GraphNode> nodes = new HashMap<>();
        private final Map<String, List<GraphEdge>> outgoing = new HashMap<>();
        private final Set<String> seen = new HashSet<>();
        private final ComponentCatalogService components;

        Parser(GraphDefinition graph, ComponentCatalogService components) {
            this.components = components;
            for (GraphNode node : graph.getNodes()) {
                nodes.put(node.getId(), node);
                outgoing.put(node.getId(), new ArrayList<>());
            }
            for (GraphEdge edge : graph.getEdges()) outgoing.get(edge.getSource()).add(edge);
            outgoing.values().forEach(list -> list.sort(Comparator.comparing(GraphEdge::getId)));
        }

        String onlyNext(String id) {
            List<GraphEdge> edges = outgoing.get(id);
            if (edges == null || edges.size() != 1) throw problem("INVALID_DEGREE", id, "Expected one outgoing edge");
            return edges.getFirst().getTarget();
        }

        String sequence(String first, String stop) {
            List<String> parts = new ArrayList<>();
            String current = first;
            while (!current.equals(stop)) {
                GraphNode node = nodes.get(current);
                if (node == null) throw problem("EDGE_TARGET_MISSING", current, "Target node does not exist");
                switch (node.getKind()) {
                    case COMPONENT -> {
                        if (!seen.add(current)) throw problem("SHARED_NODE", current, "Node appears in more than one branch");
                        parts.add(componentExpression(node));
                        current = onlyNext(current);
                    }
                    case PARALLEL_SPLIT, CONDITION -> {
                        if (node.getKind() == NodeKind.CONDITION && !seen.add(current)) {
                            throw problem("SHARED_NODE", current, "Condition appears in more than one branch");
                        }
                        List<GraphEdge> branches = outgoing.get(current);
                        checkBranches(node, branches);
                        String join = matchingJoin(node, branches);
                        List<String> branchExpressions = new ArrayList<>();
                        for (GraphEdge edge : branches) branchExpressions.add(sequence(edge.getTarget(), join));
                        if (node.getKind() == NodeKind.PARALLEL_SPLIT) {
                            parts.add("WHEN(" + String.join(",", branchExpressions) + ")");
                        } else {
                            parts.add("IF(" + componentExpression(node) + "," + String.join(",", branchExpressions) + ")");
                        }
                        current = onlyNext(join);
                    }
                    default -> throw problem("UNEXPECTED_NODE", current, "Node is outside its matching region");
                }
            }
            if (parts.isEmpty()) throw problem("EMPTY_BRANCH", first, "Branch must contain a component");
            return parts.size() == 1 ? parts.getFirst() : "THEN(" + String.join(",", parts) + ")";
        }

        private String componentExpression(GraphNode node) {
            String liteFlowNodeId = components.require(node.getComponentKey()).getLiteFlowNodeId();
            return liteFlowNodeId + ".tag(\"" + node.getId() + "\")";
        }

        private void checkBranches(GraphNode node, List<GraphEdge> branches) {
            if (node.getKind() == NodeKind.CONDITION) {
                if (branches.size() != 2 || !"true".equals(branches.getFirst().getBranch())
                        && !"false".equals(branches.getFirst().getBranch())
                        || branches.stream().filter(e -> "true".equals(e.getBranch())).count() != 1
                        || branches.stream().filter(e -> "false".equals(e.getBranch())).count() != 1) {
                    throw problem("CONDITION_BRANCHES", node.getId(), "Condition needs true and false edges");
                }
                branches.sort(Comparator.comparing(e -> "true".equals(e.getBranch()) ? 0 : 1));
            } else if (branches.size() < 2 || branches.stream().anyMatch(e -> e.getBranch() != null)) {
                throw problem("PARALLEL_BRANCHES", node.getId(), "Parallel split needs two or more unlabeled edges");
            }
        }

        private String matchingJoin(GraphNode split, List<GraphEdge> branches) {
            NodeKind joinKind = split.getKind() == NodeKind.CONDITION ? NodeKind.CONDITION_JOIN : NodeKind.PARALLEL_JOIN;
            List<Map<String, Integer>> distances = branches.stream().map(e -> distances(e.getTarget())).toList();
            return nodes.values().stream()
                    .filter(n -> n.getKind() == joinKind && distances.stream().allMatch(d -> d.containsKey(n.getId())))
                    .min(Comparator.comparingInt(n -> distances.stream().mapToInt(d -> d.get(n.getId())).sum()))
                    .map(GraphNode::getId)
                    .orElseThrow(() -> problem("JOIN_MISSING", split.getId(), "Matching join is missing"));
        }

        private Map<String, Integer> distances(String from) {
            Map<String, Integer> distance = new HashMap<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(from);
            distance.put(from, 0);
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                for (GraphEdge edge : outgoing.get(current)) {
                    if (distance.putIfAbsent(edge.getTarget(), distance.get(current) + 1) == null) queue.add(edge.getTarget());
                }
            }
            return distance;
        }
    }
}
