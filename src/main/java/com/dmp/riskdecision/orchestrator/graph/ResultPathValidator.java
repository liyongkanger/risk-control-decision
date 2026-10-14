package com.dmp.riskdecision.orchestrator.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 校验每条可执行路径恰好声明一个业务结果。 */
@Component
public class ResultPathValidator {
    private static final Set<String> RESULT_KEYS = Set.of(
            "conclusion", "reasonCode", "reason", "details", "suggestedAction");
    private static final Set<String> ACTION_KEYS = Set.of("code", "parameters");

    /**
     * 在基础图合法的前提下检查结果配置、路径数量和并行写入限制。
     *
     * <p>路径数量以拓扑方式传播，并把两个及以上结果统一计为 2，
     * 不枚举条件分支的指数级组合。</p>
     */
    public GraphValidationResult validate(GraphDefinition graph) {
        GraphValidationResult base = new GraphValidator().validateForPublish(graph);
        if (!base.isValid()) return base;
        List<GraphIssue> issues = new ArrayList<>();
        Map<String, GraphNode> nodes = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (GraphNode node : graph.getNodes()) {
            nodes.put(node.getId(), node);
            outgoing.put(node.getId(), new ArrayList<>());
            inDegree.put(node.getId(), 0);
            if (node.getKind() == NodeKind.RESULT && !validConfig(node)) {
                issues.add(issue("RESULT_CONFIG_INVALID", node.getId(), "Result configuration is invalid"));
            }
        }
        for (GraphEdge edge : graph.getEdges()) {
            outgoing.get(edge.getSource()).add(edge.getTarget());
            inDegree.put(edge.getTarget(), inDegree.get(edge.getTarget()) + 1);
        }
        checkParallelRegions(nodes, outgoing, issues);
        checkPathCounts(nodes, outgoing, inDegree, issues);
        issues.sort(Comparator.comparing(GraphIssue::getCode)
                .thenComparing(i -> i.getNodeId() == null ? "" : i.getNodeId()));
        return new GraphValidationResult(List.copyOf(issues));
    }

    private static boolean validConfig(GraphNode node) {
        if (node.getComponentKey() != null || node.getFailurePolicy() != null) return false;
        Map<String, Object> config = node.getConfig();
        if (config == null || !RESULT_KEYS.containsAll(config.keySet())) return false;
        if (!nonblank(config.get("conclusion")) || !nonblank(config.get("reasonCode"))
                || !nonblank(config.get("reason"))) return false;
        Object details = config.get("details");
        if (details != null && !(details instanceof Map)) return false;
        Object action = config.get("suggestedAction");
        if (action == null) return true;
        if (!(action instanceof Map)) return false;
        Map<?, ?> actionMap = (Map<?, ?>) action;
        if (!ACTION_KEYS.containsAll(actionMap.keySet())
                || !nonblank(actionMap.get("code"))) return false;
        Object parameters = actionMap.get("parameters");
        return parameters == null || parameters instanceof Map;
    }

    private static boolean nonblank(Object value) {
        return value instanceof String && !((String) value).isBlank();
    }

    private static void checkPathCounts(Map<String, GraphNode> nodes, Map<String, List<String>> outgoing,
                                        Map<String, Integer> inDegree, List<GraphIssue> issues) {
        Map<String, Set<Integer>> counts = new HashMap<>();
        Queue<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            counts.put(entry.getKey(), new HashSet<>());
            if (entry.getValue() == 0) ready.add(entry.getKey());
        }
        String start = nodes.values().stream().filter(n -> n.getKind() == NodeKind.START)
                .findFirst().orElseThrow().getId();
        counts.get(start).add(0);
        while (!ready.isEmpty()) {
            String id = ready.remove();
            GraphNode node = nodes.get(id);
            Set<Integer> current = new HashSet<>();
            for (int count : counts.get(id)) current.add(node.getKind() == NodeKind.RESULT ? Math.min(2, count + 1) : count);
            if (node.getKind() == NodeKind.RESULT && current.contains(2)) {
                issues.add(issue("RESULT_DUPLICATE", id, "A path reaches more than one result"));
            }
            if (node.getKind() == NodeKind.END && current.contains(0)) {
                issues.add(issue("RESULT_MISSING", id, "A path reaches END without a result"));
            }
            for (String target : outgoing.get(id)) {
                counts.get(target).addAll(current);
                int remaining = inDegree.get(target) - 1;
                inDegree.put(target, remaining);
                if (remaining == 0) ready.add(target);
            }
        }
    }

    private static void checkParallelRegions(Map<String, GraphNode> nodes, Map<String, List<String>> outgoing,
                                             List<GraphIssue> issues) {
        Set<String> flagged = new HashSet<>();
        for (GraphNode node : nodes.values()) {
            if (node.getKind() != NodeKind.PARALLEL_SPLIT) continue;
            List<String> branches = outgoing.get(node.getId());
            String join = matchingJoin(branches, nodes, outgoing);
            if (join == null) continue; // 编译器报告不匹配的汇合结构。
            for (String branch : branches) {
                Set<String> region = walkUntil(branch, join, outgoing);
                for (String id : region) {
                    if (nodes.get(id).getKind() == NodeKind.RESULT && flagged.add(id)) {
                        issues.add(issue("RESULT_PARALLEL_UNSUPPORTED", id,
                                "Result must be chosen after parallel branches join"));
                    }
                }
            }
        }
        for (GraphNode node : nodes.values()) {
            if (node.getKind() != NodeKind.RESULT || flagged.contains(node.getId())) continue;
            for (String id : walkUntil(node.getId(), null, outgoing)) {
                if (nodes.get(id).getKind() == NodeKind.PARALLEL_SPLIT) {
                    issues.add(issue("RESULT_PARALLEL_UNSUPPORTED", node.getId(),
                            "Parallel split cannot follow a result"));
                    break;
                }
            }
        }
    }

    private static String matchingJoin(List<String> branches, Map<String, GraphNode> nodes,
                                       Map<String, List<String>> outgoing) {
        List<Map<String, Integer>> distances = new ArrayList<>();
        for (String branch : branches) distances.add(distances(branch, outgoing));
        return nodes.values().stream().filter(n -> n.getKind() == NodeKind.PARALLEL_JOIN
                        && distances.stream().allMatch(d -> d.containsKey(n.getId())))
                .min(Comparator.comparingInt(n -> distances.stream().mapToInt(d -> d.get(n.getId())).sum()))
                .map(GraphNode::getId).orElse(null);
    }

    private static Map<String, Integer> distances(String from, Map<String, List<String>> outgoing) {
        Map<String, Integer> distance = new HashMap<>();
        Queue<String> pending = new ArrayDeque<>();
        pending.add(from);
        distance.put(from, 0);
        while (!pending.isEmpty()) {
            String id = pending.remove();
            for (String target : outgoing.get(id)) {
                if (distance.putIfAbsent(target, distance.get(id) + 1) == null) pending.add(target);
            }
        }
        return distance;
    }

    private static Set<String> walkUntil(String from, String stop, Map<String, List<String>> outgoing) {
        Set<String> visited = new HashSet<>();
        Queue<String> pending = new ArrayDeque<>();
        pending.add(from);
        while (!pending.isEmpty()) {
            String id = pending.remove();
            if (id.equals(stop) || !visited.add(id)) continue;
            pending.addAll(outgoing.get(id));
        }
        return visited;
    }

    private static GraphIssue issue(String code, String nodeId, String message) {
        return new GraphIssue(code, nodeId, null, message);
    }
}
