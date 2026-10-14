package com.dmp.riskdecision.policy.service.impl;

import com.dmp.riskdecision.component.liteflow.service.ComponentCatalogService;
import com.dmp.riskdecision.component.liteflow.service.ComponentDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphCompilationException;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.GraphSourceAvailability;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 检查流程节点引用的组件类型和配置。 */
@Service
public class FlowComponentValidationService {
    @Resource
    private ComponentCatalogService components;

    public FlowComponentValidationService() {
    }

    public FlowComponentValidationService(ComponentCatalogService components) {
        this.components = components;
    }

    public void requireValid(GraphDefinition graph) {
        for (GraphNode node : graph.getNodes()) {
            if (node.getKind() != NodeKind.COMPONENT && node.getKind() != NodeKind.CONDITION) continue;
            ComponentDefinition definition;
            try {
                definition = components.require(node.getComponentKey());
            } catch (IllegalArgumentException error) {
                throw new GraphCompilationException(List.of(new GraphIssue("COMPONENT_UNKNOWN", node.getId(), null,
                        "Component is not registered")));
            }
            if (definition.isCondition() != (node.getKind() == NodeKind.CONDITION)) {
                throw new GraphCompilationException(List.of(new GraphIssue("COMPONENT_KIND_MISMATCH", node.getId(), null,
                        "Component kind does not match node kind")));
            }
            Map<String, Object> config = node.getConfig();
            boolean invalidCondition = config != null && node.getKind() == NodeKind.CONDITION
                    && ("equals-value".equals(node.getComponentKey())
                    ? !(config.get("sourceNodeId") instanceof String)
                        || !(config.get("path") instanceof String)
                        || !((String) config.get("path")).matches("[A-Za-z][A-Za-z0-9_.]{0,127}")
                        || !(config.get("expected") instanceof String)
                    : !(config.get("inputKey") instanceof String));
            if (config == null || (node.getKind() == NodeKind.COMPONENT && config.get("value") == null)
                    || invalidCondition) {
                throw new GraphCompilationException(List.of(new GraphIssue("COMPONENT_CONFIG_INVALID", node.getId(), null,
                        "Component configuration is invalid")));
            }
            if (node.getKind() == NodeKind.CONDITION && "equals-value".equals(node.getComponentKey())
                    && !GraphSourceAvailability.isAvailable(graph, (String) config.get("sourceNodeId"), node.getId())) {
                throw new GraphCompilationException(List.of(new GraphIssue("CONDITION_SOURCE_UNAVAILABLE",
                        node.getId(), null, "Condition source must execute on every path before this node")));
            }
        }
    }
}
