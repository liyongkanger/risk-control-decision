package com.dmp.riskdecision.policy.service.impl;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.GraphValidationResult;
import com.dmp.riskdecision.orchestrator.graph.GraphSourceAvailability;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.repository.FlowAssetRepository;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 校验子流程引用的当前发布版本、层级和输入映射。 */
@Service
public class SubflowReferenceValidator {
    @Resource
    private FlowAssetRepository repository;

    public SubflowReferenceValidator() {
    }

    public SubflowReferenceValidator(FlowAssetRepository repository) {
        this.repository = repository;
    }

    /** 对已通过基础结构检查的外层流程逐个校验调用节点。 */
    public GraphValidationResult validate(String ownerFlowCode, GraphDefinition graph) {
        List<GraphIssue> issues = new ArrayList<>();
        for (GraphNode node : graph.getNodes()) {
            if (node.getKind() != NodeKind.SUBFLOW) continue;
            Map<String, Object> config = node.getConfig();
            if (!validShape(node, config)) {
                issues.add(issue("SUBFLOW_CONFIG_INVALID", node.getId(), "Subflow configuration is invalid"));
                continue;
            }
            String childCode = (String) config.get("flowCode");
            if (childCode.equals(ownerFlowCode)) {
                issues.add(issue("SUBFLOW_CYCLE", node.getId(), "A flow cannot call itself"));
                continue;
            }
            FlowVersion child;
            try {
                child = repository.findPublished(childCode).orElse(null);
            } catch (NoSuchElementException error) {
                child = null;
            }
            if (child == null || child.getId() != ((Number) config.get("versionId")).longValue()
                    || !childCode.equals(child.getFlowCode())) {
                issues.add(issue("SUBFLOW_VERSION_UNAVAILABLE", node.getId(),
                        "Selected version is not the flow's current published version"));
                continue;
            }
            if (child.getGraph().getNodes().stream().anyMatch(n -> n.getKind() == NodeKind.SUBFLOW)) {
                issues.add(issue("SUBFLOW_NESTING_UNSUPPORTED", node.getId(),
                        "A called flow cannot contain another subflow call"));
            }
            if (!validMappings(node, child, graph)) {
                issues.add(issue("SUBFLOW_INPUT_MAPPING_INVALID", node.getId(),
                        "Input mappings do not match the called flow's required inputs or upstream nodes"));
            }
        }
        return new GraphValidationResult(List.copyOf(issues));
    }

    /** 从已经校验的外层图中收集需在发布指针切换时再次核对的版本。 */
    public static Map<String, Long> pinnedVersions(GraphDefinition graph) {
        Map<String, Long> pinned = new HashMap<>();
        for (GraphNode node : graph.getNodes()) {
            if (node.getKind() == NodeKind.SUBFLOW) {
                pinned.put((String) node.getConfig().get("flowCode"),
                        ((Number) node.getConfig().get("versionId")).longValue());
            }
        }
        return Map.copyOf(pinned);
    }

    private static boolean validShape(GraphNode node, Map<String, Object> config) {
        if (node.getComponentKey() != null || node.getFailurePolicy() != FailurePolicy.FAIL_FAST
                || config == null || !Set.of("flowCode", "versionId", "inputMappings").containsAll(config.keySet())) {
            return false;
        }
        if (!(config.get("flowCode") instanceof String)
                || !((String) config.get("flowCode")).matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) return false;
        if (!(config.get("versionId") instanceof Long)
                && !(config.get("versionId") instanceof Integer)
                && !(config.get("versionId") instanceof Short)) return false;
        if (((Number) config.get("versionId")).longValue() < 1) return false;
        return config.get("inputMappings") instanceof Map;
    }

    private static boolean validMappings(GraphNode call, FlowVersion child, GraphDefinition graph) {
        Map<?, ?> mappings = (Map<?, ?>) call.getConfig().get("inputMappings");
        if (!new HashSet<>(mappings.keySet()).equals(new HashSet<>(child.getGraph().getRequiredInputs()))) {
            return false;
        }
        for (Object value : mappings.values()) {
            if (!(value instanceof Map)) return false;
            Map<?, ?> source = (Map<?, ?>) value;
            if ("REQUEST_INPUT".equals(source.get("source"))) {
                if (!(source.get("key") instanceof String) || ((String) source.get("key")).isBlank()
                        || !Set.of("source", "key").containsAll(source.keySet())) return false;
            } else if ("NODE_RESULT".equals(source.get("source"))) {
                if (!(source.get("nodeId") instanceof String) || !(source.get("path") instanceof String)
                        || !((String) source.get("path")).matches("[A-Za-z][A-Za-z0-9_.]{0,127}")
                        || !Set.of("source", "nodeId", "path").containsAll(source.keySet())) return false;
                String from = (String) source.get("nodeId");
                if (!GraphSourceAvailability.isAvailable(graph, from, call.getId())) return false;
            } else {
                return false;
            }
        }
        return true;
    }

    private static GraphIssue issue(String code, String nodeId, String message) {
        return new GraphIssue(code, nodeId, null, message);
    }
}
