package com.dmp.riskdecision.orchestrator.graph;

import java.util.Map;
import java.util.Objects;

/** 流程图节点。 */
public class GraphNode {
    private String id;
    private NodeKind kind;
    private String componentKey;
    private FailurePolicy failurePolicy;
    private Map<String, Object> config;

    public GraphNode() {
    }

    public GraphNode(String id, NodeKind kind, String componentKey, FailurePolicy failurePolicy, Map<String, Object> config) {
        this.id = id;
        this.kind = kind;
        this.componentKey = componentKey;
        this.failurePolicy = failurePolicy;
        this.config = config;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NodeKind getKind() {
        return kind;
    }

    public void setKind(NodeKind kind) {
        this.kind = kind;
    }

    public String getComponentKey() {
        return componentKey;
    }

    public void setComponentKey(String componentKey) {
        this.componentKey = componentKey;
    }

    public FailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    public void setFailurePolicy(FailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphNode)) {
            return false;
        }
        GraphNode that = (GraphNode) other;
        return Objects.equals(id, that.id)
                && Objects.equals(kind, that.kind)
                && Objects.equals(componentKey, that.componentKey)
                && Objects.equals(failurePolicy, that.failurePolicy)
                && Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, kind, componentKey, failurePolicy, config);
    }
}
