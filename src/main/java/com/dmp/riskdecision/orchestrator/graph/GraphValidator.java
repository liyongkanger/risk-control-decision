package com.dmp.riskdecision.orchestrator.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

/** 检查流程图结构、分支和发布约束。 */
@org.springframework.stereotype.Component
public class GraphValidator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_GRAPH_BYTES = 1_048_576;

    /** 校验草稿的基本字段、节点和连线，不要求完整的可执行结构。 */
    public GraphValidationResult validateDraft(GraphDefinition graph) {
        List<GraphIssue> issues = new ArrayList<>();
        if (graph == null) {
            issues.add(issue("GRAPH_REQUIRED", null, null, "Graph is required"));
            return result(issues);
        }
        if (graph.getSchemaVersion() != 1) {
            issues.add(issue("SCHEMA_VERSION_UNSUPPORTED", null, null, "Only schema version 1 is supported"));
        }
        List<GraphNode> nodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        List<String> requiredInputs = graph.getRequiredInputs() == null ? List.of() : graph.getRequiredInputs();
        Set<String> inputNames = new HashSet<>();
        for (String inputName : requiredInputs) {
            if (inputName == null || !inputName.matches("[A-Za-z][A-Za-z0-9_]{0,63}")
                    || !inputNames.add(inputName)) {
                issues.add(issue("INVALID_REQUIRED_INPUT", null, null, "Required input names must be unique identifiers"));
            }
        }
        List<GraphEdge> edges = graph.getEdges() == null ? List.of() : graph.getEdges();
        if (nodes.size() > 200 || edges.size() > 500) {
            issues.add(issue("GRAPH_TOO_LARGE", null, null, "Graph exceeds 200 nodes or 500 edges"));
        }
        if (graph.getLayout() != null && graph.getLayout().size() > 200) {
            issues.add(issue("GRAPH_TOO_LARGE", null, null, "Graph layout exceeds 200 entries"));
        }
        if (JSON.writeValueAsBytes(graph).length > MAX_GRAPH_BYTES) {
            issues.add(issue("GRAPH_TOO_LARGE", null, null, "Graph exceeds 1 MiB"));
        }

        Map<String, GraphNode> nodeById = new HashMap<>();
        for (GraphNode node : nodes) {
            if (node == null || blank(node.getId()) || node.getKind() == null
                    || !node.getId().matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
                issues.add(issue("INVALID_NODE", node == null ? null : node.getId(), null, "Node ID and kind are required"));
                continue;
            }
            if (nodeById.putIfAbsent(node.getId(), node) != null) {
                issues.add(issue("DUPLICATE_NODE_ID", node.getId(), null, "Node ID is duplicated"));
            }
        }

        Set<String> edgeIds = new HashSet<>();
        for (GraphEdge edge : edges) {
            if (edge == null || blank(edge.getId()) || blank(edge.getSource()) || blank(edge.getTarget())) {
                issues.add(issue("INVALID_EDGE", null, edge == null ? null : edge.getId(), "Edge ID and endpoints are required"));
                continue;
            }
            if (!edgeIds.add(edge.getId())) {
                issues.add(issue("DUPLICATE_EDGE_ID", null, edge.getId(), "Edge ID is duplicated"));
            }
            if (!nodeById.containsKey(edge.getSource())) {
                issues.add(issue("EDGE_SOURCE_MISSING", null, edge.getId(), "Edge source does not exist"));
            }
            if (!nodeById.containsKey(edge.getTarget())) {
                issues.add(issue("EDGE_TARGET_MISSING", null, edge.getId(), "Edge target does not exist"));
            }
        }
        return result(issues);
    }

    /**
     * 校验图能否发布为可执行流程。
     *
     * <p>在 {@link #validateDraft(GraphDefinition)} 的结果上检查入口、出口、
     * 条件分支、并行汇合、可达性与环路。</p>
     */
    public GraphValidationResult validateForPublish(GraphDefinition graph) {
        List<GraphIssue> issues = new ArrayList<>(validateDraft(graph).getIssues());
        if (graph == null) return result(issues);
        List<GraphNode> nodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        List<GraphEdge> edges = graph.getEdges() == null ? List.of() : graph.getEdges();
        if (issues.stream().anyMatch(i -> i.getCode().startsWith("INVALID_")
                || i.getCode().startsWith("DUPLICATE_") || i.getCode().startsWith("EDGE_")
                || i.getCode().equals("GRAPH_TOO_LARGE"))) return result(issues);

        Map<String, GraphNode> nodeById = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        Map<String, List<GraphEdge>> outgoingEdges = new HashMap<>();
        for (GraphNode node : nodes) {
            nodeById.put(node.getId(), node);
            outgoing.put(node.getId(), new ArrayList<>());
            incoming.put(node.getId(), new ArrayList<>());
            outgoingEdges.put(node.getId(), new ArrayList<>());
        }
        for (GraphEdge edge : edges) {
            outgoing.get(edge.getSource()).add(edge.getTarget());
            incoming.get(edge.getTarget()).add(edge.getSource());
            outgoingEdges.get(edge.getSource()).add(edge);
        }

        List<GraphNode> starts = nodes.stream().filter(n -> n.getKind() == NodeKind.START).toList();
        List<GraphNode> ends = nodes.stream().filter(n -> n.getKind() == NodeKind.END).toList();
        if (starts.size() != 1) issues.add(issue("START_COUNT", null, null, "Exactly one START is required"));
        if (ends.size() != 1) issues.add(issue("END_COUNT", null, null, "Exactly one END is required"));
        for (GraphNode node : nodes) {
            int in = incoming.get(node.getId()).size();
            int out = outgoing.get(node.getId()).size();
            if ((node.getKind() == NodeKind.START && (in != 0 || out != 1))
                    || (node.getKind() == NodeKind.END && (in != 1 || out != 0))
                    || ((node.getKind() == NodeKind.COMPONENT || node.getKind() == NodeKind.RESULT
                    || node.getKind() == NodeKind.SUBFLOW)
                    && (in != 1 || out != 1))) {
                issues.add(issue("INVALID_DEGREE", node.getId(), null, "Invalid number of incoming or outgoing edges"));
            }
            if (node.getKind() == NodeKind.COMPONENT) {
                if (blank(node.getComponentKey())) issues.add(issue("COMPONENT_KEY_REQUIRED", node.getId(), null, "Component key is required"));
                if (node.getFailurePolicy() != FailurePolicy.FAIL_FAST) {
                    issues.add(issue("FAILURE_POLICY_REQUIRED", node.getId(), null, "FAIL_FAST is required"));
                }
            }
            if (node.getKind() == NodeKind.PARALLEL_SPLIT) {
                if (in != 1 || out < 2) issues.add(issue("INVALID_DEGREE", node.getId(), null, "Parallel split needs one input and at least two outputs"));
                if (outgoingEdges.get(node.getId()).stream().anyMatch(e -> e.getBranch() != null)) {
                    issues.add(issue("PARALLEL_BRANCHES", node.getId(), null, "Parallel edges must not have branch labels"));
                }
            }
            if (node.getKind() == NodeKind.CONDITION) {
                if (in != 1 || out != 2) issues.add(issue("INVALID_DEGREE", node.getId(), null, "Condition needs one input and two outputs"));
                if (blank(node.getComponentKey())) issues.add(issue("COMPONENT_KEY_REQUIRED", node.getId(), null, "Condition component key is required"));
                if (node.getFailurePolicy() != FailurePolicy.FAIL_FAST) {
                    issues.add(issue("FAILURE_POLICY_REQUIRED", node.getId(), null, "FAIL_FAST is required"));
                }
                List<GraphEdge> branches = outgoingEdges.get(node.getId());
                if (branches.stream().filter(e -> "true".equals(e.getBranch())).count() != 1
                        || branches.stream().filter(e -> "false".equals(e.getBranch())).count() != 1) {
                    issues.add(issue("CONDITION_BRANCHES", node.getId(), null, "Condition needs true and false edges"));
                }
            }
            if (node.getKind() == NodeKind.PARALLEL_JOIN && (in < 2 || out != 1)) {
                issues.add(issue("INVALID_DEGREE", node.getId(), null, "Parallel join needs at least two inputs and one output"));
            }
            if (node.getKind() == NodeKind.CONDITION_JOIN && (in != 2 || out != 1)) {
                issues.add(issue("INVALID_DEGREE", node.getId(), null, "Condition join needs two inputs and one output"));
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
            Set<String> reachable = walk(starts.getFirst().getId(), outgoing);
            for (GraphNode node : nodes) {
                if (!reachable.contains(node.getId())) issues.add(issue("NODE_UNREACHABLE", node.getId(), null, "Node is not reachable from START"));
            }
        }
        if (ends.size() == 1) {
            Set<String> reachesEnd = walk(ends.getFirst().getId(), incoming);
            for (GraphNode node : nodes) {
                if (!reachesEnd.contains(node.getId())) issues.add(issue("NODE_CANNOT_REACH_END", node.getId(), null, "Node cannot reach END"));
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
        issues.sort(Comparator.comparing(GraphIssue::getCode)
                .thenComparing(i -> i.getNodeId() == null ? "" : i.getNodeId())
                .thenComparing(i -> i.getEdgeId() == null ? "" : i.getEdgeId()));
        return new GraphValidationResult(List.copyOf(issues));
    }

    private static GraphIssue issue(String code, String nodeId, String edgeId, String message) {
        return new GraphIssue(code, nodeId, edgeId, message);
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
