package com.dmp.riskdecision.orchestrator.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphValidator {

    public GraphValidationResult validateDraft(GraphDefinition graph) {
        List<GraphIssue> issues = new ArrayList<>();
        if (graph == null) {
            issues.add(issue("GRAPH_REQUIRED", null, null, "Graph is required"));
            return result(issues);
        }
        if (graph.schemaVersion() != 1) {
            issues.add(issue("SCHEMA_VERSION_UNSUPPORTED", null, null, "Only schema version 1 is supported"));
        }
        List<GraphNode> nodes = graph.nodes() == null ? List.of() : graph.nodes();
        List<GraphEdge> edges = graph.edges() == null ? List.of() : graph.edges();
        if (nodes.size() > 200 || edges.size() > 500) {
            issues.add(issue("GRAPH_TOO_LARGE", null, null, "Graph exceeds 200 nodes or 500 edges"));
        }

        Map<String, GraphNode> nodeById = new HashMap<>();
        for (GraphNode node : nodes) {
            if (node == null || blank(node.id()) || node.kind() == null) {
                issues.add(issue("INVALID_NODE", node == null ? null : node.id(), null, "Node ID and kind are required"));
                continue;
            }
            if (nodeById.putIfAbsent(node.id(), node) != null) {
                issues.add(issue("DUPLICATE_NODE_ID", node.id(), null, "Node ID is duplicated"));
            }
        }

        Set<String> edgeIds = new HashSet<>();
        for (GraphEdge edge : edges) {
            if (edge == null || blank(edge.id()) || blank(edge.source()) || blank(edge.target())) {
                issues.add(issue("INVALID_EDGE", null, edge == null ? null : edge.id(), "Edge ID and endpoints are required"));
                continue;
            }
            if (!edgeIds.add(edge.id())) {
                issues.add(issue("DUPLICATE_EDGE_ID", null, edge.id(), "Edge ID is duplicated"));
            }
            if (!nodeById.containsKey(edge.source())) {
                issues.add(issue("EDGE_SOURCE_MISSING", null, edge.id(), "Edge source does not exist"));
            }
            if (!nodeById.containsKey(edge.target())) {
                issues.add(issue("EDGE_TARGET_MISSING", null, edge.id(), "Edge target does not exist"));
            }
        }
        return result(issues);
    }

    public GraphValidationResult validateForPublish(GraphDefinition graph) {
        List<GraphIssue> issues = new ArrayList<>(validateDraft(graph).issues());
        if (graph == null) return result(issues);
        List<GraphNode> nodes = graph.nodes() == null ? List.of() : graph.nodes();
        List<GraphEdge> edges = graph.edges() == null ? List.of() : graph.edges();
        if (issues.stream().anyMatch(i -> i.code().startsWith("INVALID_")
                || i.code().startsWith("DUPLICATE_") || i.code().startsWith("EDGE_")
                || i.code().equals("GRAPH_TOO_LARGE"))) return result(issues);

        Map<String, GraphNode> nodeById = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        Map<String, List<GraphEdge>> outgoingEdges = new HashMap<>();
        for (GraphNode node : nodes) {
            nodeById.put(node.id(), node);
            outgoing.put(node.id(), new ArrayList<>());
            incoming.put(node.id(), new ArrayList<>());
            outgoingEdges.put(node.id(), new ArrayList<>());
        }
        for (GraphEdge edge : edges) {
            outgoing.get(edge.source()).add(edge.target());
            incoming.get(edge.target()).add(edge.source());
            outgoingEdges.get(edge.source()).add(edge);
        }

        List<GraphNode> starts = nodes.stream().filter(n -> n.kind() == NodeKind.START).toList();
        List<GraphNode> ends = nodes.stream().filter(n -> n.kind() == NodeKind.END).toList();
        if (starts.size() != 1) issues.add(issue("START_COUNT", null, null, "Exactly one START is required"));
        if (ends.size() != 1) issues.add(issue("END_COUNT", null, null, "Exactly one END is required"));
        for (GraphNode node : nodes) {
            int in = incoming.get(node.id()).size();
            int out = outgoing.get(node.id()).size();
            if ((node.kind() == NodeKind.START && (in != 0 || out != 1))
                    || (node.kind() == NodeKind.END && (in != 1 || out != 0))
                    || (node.kind() == NodeKind.COMPONENT && (in != 1 || out != 1))) {
                issues.add(issue("INVALID_DEGREE", node.id(), null, "Invalid number of incoming or outgoing edges"));
            }
            if (node.kind() == NodeKind.COMPONENT) {
                if (blank(node.componentKey())) issues.add(issue("COMPONENT_KEY_REQUIRED", node.id(), null, "Component key is required"));
                if (node.failurePolicy() != FailurePolicy.FAIL_FAST) {
                    issues.add(issue("FAILURE_POLICY_REQUIRED", node.id(), null, "FAIL_FAST is required"));
                }
            }
            if (node.kind() == NodeKind.PARALLEL_SPLIT) {
                if (in != 1 || out < 2) issues.add(issue("INVALID_DEGREE", node.id(), null, "Parallel split needs one input and at least two outputs"));
                if (outgoingEdges.get(node.id()).stream().anyMatch(e -> e.branch() != null)) {
                    issues.add(issue("PARALLEL_BRANCHES", node.id(), null, "Parallel edges must not have branch labels"));
                }
            }
            if (node.kind() == NodeKind.CONDITION) {
                if (in != 1 || out != 2) issues.add(issue("INVALID_DEGREE", node.id(), null, "Condition needs one input and two outputs"));
                if (blank(node.componentKey())) issues.add(issue("COMPONENT_KEY_REQUIRED", node.id(), null, "Condition component key is required"));
                List<GraphEdge> branches = outgoingEdges.get(node.id());
                if (branches.stream().filter(e -> "true".equals(e.branch())).count() != 1
                        || branches.stream().filter(e -> "false".equals(e.branch())).count() != 1) {
                    issues.add(issue("CONDITION_BRANCHES", node.id(), null, "Condition needs true and false edges"));
                }
            }
            if (node.kind() == NodeKind.PARALLEL_JOIN && (in < 2 || out != 1)) {
                issues.add(issue("INVALID_DEGREE", node.id(), null, "Parallel join needs at least two inputs and one output"));
            }
            if (node.kind() == NodeKind.CONDITION_JOIN && (in != 2 || out != 1)) {
                issues.add(issue("INVALID_DEGREE", node.id(), null, "Condition join needs two inputs and one output"));
            }
        }

        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();
        boolean cycle = false;
        for (String id : nodeById.keySet()) {
            if (hasCycle(id, outgoing, visited, active)) cycle = true;
        }
        if (cycle) issues.add(issue("GRAPH_CYCLE", null, null, "Graph contains a cycle"));
        if (starts.size() == 1) {
            Set<String> reachable = walk(starts.getFirst().id(), outgoing);
            for (GraphNode node : nodes) {
                if (!reachable.contains(node.id())) issues.add(issue("NODE_UNREACHABLE", node.id(), null, "Node is not reachable from START"));
            }
        }
        if (ends.size() == 1) {
            Set<String> reachesEnd = walk(ends.getFirst().id(), incoming);
            for (GraphNode node : nodes) {
                if (!reachesEnd.contains(node.id())) issues.add(issue("NODE_CANNOT_REACH_END", node.id(), null, "Node cannot reach END"));
            }
        }
        return result(issues);
    }

    private static boolean hasCycle(String id, Map<String, List<String>> outgoing,
                                    Set<String> visited, Set<String> active) {
        if (active.contains(id)) return true;
        if (!visited.add(id)) return false;
        active.add(id);
        boolean cycle = false;
        for (String next : outgoing.get(id)) cycle |= hasCycle(next, outgoing, visited, active);
        active.remove(id);
        return cycle;
    }

    private static Set<String> walk(String from, Map<String, List<String>> links) {
        Set<String> seen = new HashSet<>();
        List<String> pending = new ArrayList<>(List.of(from));
        while (!pending.isEmpty()) {
            String current = pending.removeLast();
            if (seen.add(current)) pending.addAll(links.get(current));
        }
        return seen;
    }

    private static GraphValidationResult result(List<GraphIssue> issues) {
        issues.sort(Comparator.comparing(GraphIssue::code)
                .thenComparing(i -> i.nodeId() == null ? "" : i.nodeId())
                .thenComparing(i -> i.edgeId() == null ? "" : i.edgeId()));
        return new GraphValidationResult(List.copyOf(issues));
    }

    private static GraphIssue issue(String code, String nodeId, String edgeId, String message) {
        return new GraphIssue(code, nodeId, edgeId, message);
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
