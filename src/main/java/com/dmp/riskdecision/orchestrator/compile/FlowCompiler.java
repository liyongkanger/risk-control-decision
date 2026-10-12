package com.dmp.riskdecision.orchestrator.compile;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.GraphValidator;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlowCompiler {
    private final GraphValidator validator = new GraphValidator();

    public CompiledFlow compile(String sceneCode, long version, GraphDefinition graph) {
        if (sceneCode == null || !sceneCode.matches("[A-Za-z][A-Za-z0-9_-]{0,63}") || version < 1) {
            throw new IllegalArgumentException("Invalid scene code or version");
        }
        var validation = validator.validateForPublish(graph);
        if (!validation.valid()) throw new GraphCompilationException(validation.issues());
        Parser parser = new Parser(graph);
        String start = graph.nodes().stream().filter(n -> n.kind() == NodeKind.START).findFirst().orElseThrow().id();
        String end = graph.nodes().stream().filter(n -> n.kind() == NodeKind.END).findFirst().orElseThrow().id();
        String el = parser.sequence(parser.onlyNext(start), end);
        if (parser.seen.size() != graph.nodes().stream().filter(n -> n.kind() == NodeKind.COMPONENT
                || n.kind() == NodeKind.CONDITION).count()) {
            throw problem("UNCONSUMED_NODE", null, "Graph contains nodes outside the compiled structure");
        }
        Map<String, String> bindings = new HashMap<>();
        for (GraphNode node : graph.nodes()) {
            if (node.kind() == NodeKind.COMPONENT || node.kind() == NodeKind.CONDITION) {
                bindings.put(node.id(), node.id());
            }
        }
        return new CompiledFlow("scene_" + sceneCode + "_v" + version, el, Map.copyOf(bindings));
    }

    private static GraphCompilationException problem(String code, String nodeId, String message) {
        return new GraphCompilationException(List.of(new GraphIssue(code, nodeId, null, message)));
    }

    private static final class Parser {
        private final Map<String, GraphNode> nodes = new HashMap<>();
        private final Map<String, List<GraphEdge>> outgoing = new HashMap<>();
        private final Set<String> seen = new HashSet<>();

        Parser(GraphDefinition graph) {
            for (GraphNode node : graph.nodes()) {
                nodes.put(node.id(), node);
                outgoing.put(node.id(), new ArrayList<>());
            }
            for (GraphEdge edge : graph.edges()) outgoing.get(edge.source()).add(edge);
            outgoing.values().forEach(list -> list.sort(Comparator.comparing(GraphEdge::id)));
        }

        String onlyNext(String id) {
            List<GraphEdge> edges = outgoing.get(id);
            if (edges == null || edges.size() != 1) throw problem("INVALID_DEGREE", id, "Expected one outgoing edge");
            return edges.getFirst().target();
        }

        String sequence(String first, String stop) {
            List<String> parts = new ArrayList<>();
            String current = first;
            while (!current.equals(stop)) {
                GraphNode node = nodes.get(current);
                if (node == null) throw problem("EDGE_TARGET_MISSING", current, "Target node does not exist");
                switch (node.kind()) {
                    case COMPONENT -> {
                        if (!seen.add(current)) throw problem("SHARED_NODE", current, "Node appears in more than one branch");
                        parts.add(current);
                        current = onlyNext(current);
                    }
                    case PARALLEL_SPLIT, CONDITION -> {
                        if (node.kind() == NodeKind.CONDITION && !seen.add(current)) {
                            throw problem("SHARED_NODE", current, "Condition appears in more than one branch");
                        }
                        List<GraphEdge> branches = outgoing.get(current);
                        checkBranches(node, branches);
                        String join = matchingJoin(node, branches);
                        List<String> branchExpressions = new ArrayList<>();
                        for (GraphEdge edge : branches) branchExpressions.add(sequence(edge.target(), join));
                        if (node.kind() == NodeKind.PARALLEL_SPLIT) {
                            parts.add("WHEN(" + String.join(",", branchExpressions) + ")");
                        } else {
                            parts.add("IF(" + current + "," + String.join(",", branchExpressions) + ")");
                        }
                        current = onlyNext(join);
                    }
                    default -> throw problem("UNEXPECTED_NODE", current, "Node is outside its matching region");
                }
            }
            if (parts.isEmpty()) throw problem("EMPTY_BRANCH", first, "Branch must contain a component");
            return parts.size() == 1 ? parts.getFirst() : "THEN(" + String.join(",", parts) + ")";
        }

        private void checkBranches(GraphNode node, List<GraphEdge> branches) {
            if (node.kind() == NodeKind.CONDITION) {
                if (branches.size() != 2 || !"true".equals(branches.getFirst().branch())
                        && !"false".equals(branches.getFirst().branch())
                        || branches.stream().filter(e -> "true".equals(e.branch())).count() != 1
                        || branches.stream().filter(e -> "false".equals(e.branch())).count() != 1) {
                    throw problem("CONDITION_BRANCHES", node.id(), "Condition needs true and false edges");
                }
                branches.sort(Comparator.comparing(e -> "true".equals(e.branch()) ? 0 : 1));
            } else if (branches.size() < 2 || branches.stream().anyMatch(e -> e.branch() != null)) {
                throw problem("PARALLEL_BRANCHES", node.id(), "Parallel split needs two or more unlabeled edges");
            }
        }

        private String matchingJoin(GraphNode split, List<GraphEdge> branches) {
            NodeKind joinKind = split.kind() == NodeKind.CONDITION ? NodeKind.CONDITION_JOIN : NodeKind.PARALLEL_JOIN;
            List<Map<String, Integer>> distances = branches.stream().map(e -> distances(e.target())).toList();
            return nodes.values().stream()
                    .filter(n -> n.kind() == joinKind && distances.stream().allMatch(d -> d.containsKey(n.id())))
                    .min(Comparator.comparingInt(n -> distances.stream().mapToInt(d -> d.get(n.id())).sum()))
                    .map(GraphNode::id)
                    .orElseThrow(() -> problem("JOIN_MISSING", split.id(), "Matching join is missing"));
        }

        private Map<String, Integer> distances(String from) {
            Map<String, Integer> distance = new HashMap<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(from);
            distance.put(from, 0);
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                for (GraphEdge edge : outgoing.get(current)) {
                    if (distance.putIfAbsent(edge.target(), distance.get(current) + 1) == null) queue.add(edge.target());
                }
            }
            return distance;
        }
    }
}
