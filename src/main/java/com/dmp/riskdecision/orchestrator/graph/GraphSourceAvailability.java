package com.dmp.riskdecision.orchestrator.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** 检查结果来源是否在目标节点的每条执行路径上已经出现。 */
public final class GraphSourceAvailability {
    private GraphSourceAvailability() {
    }

    /** 条件汇合取两支共有结果，并行汇合保留所有分支都已执行的结果。 */
    public static boolean isAvailable(GraphDefinition graph, String sourceId, String targetId) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) return false;
        Map<String, GraphNode> nodes = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        Map<String, Integer> degree = new HashMap<>();
        for (GraphNode node : graph.getNodes()) {
            nodes.put(node.getId(), node);
            outgoing.put(node.getId(), new ArrayList<>());
            incoming.put(node.getId(), new ArrayList<>());
            degree.put(node.getId(), 0);
        }
        if (!nodes.containsKey(sourceId) || !nodes.containsKey(targetId)) return false;
        for (GraphEdge edge : graph.getEdges()) {
            outgoing.get(edge.getSource()).add(edge.getTarget());
            incoming.get(edge.getTarget()).add(edge.getSource());
            degree.put(edge.getTarget(), degree.get(edge.getTarget()) + 1);
        }
        Map<String, Set<String>> guaranteedAfter = new HashMap<>();
        Map<String, Set<String>> guaranteedBefore = new HashMap<>();
        Queue<String> ready = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : degree.entrySet()) {
            if (entry.getValue() == 0) ready.add(entry.getKey());
        }
        while (!ready.isEmpty()) {
            String id = ready.remove();
            List<String> predecessors = incoming.get(id);
            Set<String> before = new HashSet<>();
            if (!predecessors.isEmpty()) {
                before.addAll(guaranteedAfter.get(predecessors.getFirst()));
                for (int i = 1; i < predecessors.size(); i++) {
                    if (nodes.get(id).getKind() == NodeKind.PARALLEL_JOIN) {
                        before.addAll(guaranteedAfter.get(predecessors.get(i)));
                    } else {
                        before.retainAll(guaranteedAfter.get(predecessors.get(i)));
                    }
                }
            }
            guaranteedBefore.put(id, before);
            Set<String> after = new HashSet<>(before);
            after.add(id);
            guaranteedAfter.put(id, after);
            for (String next : outgoing.get(id)) {
                int remaining = degree.get(next) - 1;
                degree.put(next, remaining);
                if (remaining == 0) ready.add(next);
            }
        }
        Set<String> available = guaranteedBefore.get(targetId);
        return available != null && available.contains(sourceId);
    }
}
