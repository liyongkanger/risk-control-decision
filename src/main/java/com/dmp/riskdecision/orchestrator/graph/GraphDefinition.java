package com.dmp.riskdecision.orchestrator.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 流程图定义。 */
public class GraphDefinition {
    private int schemaVersion;
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
    private Map<String, Point> layout;
    private List<String> requiredInputs = List.of();
    private List<FlowContextBinding> contextBindings = List.of();

    public GraphDefinition() {
    }

    public GraphDefinition(int schemaVersion, List<GraphNode> nodes, List<GraphEdge> edges, Map<String, Point> layout) {
        this.schemaVersion = schemaVersion;
        this.nodes = nodes;
        this.edges = edges;
        this.layout = layout;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public List<GraphNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphNode> nodes) {
        this.nodes = nodes;
    }

    public List<GraphEdge> getEdges() {
        return edges;
    }

    public void setEdges(List<GraphEdge> edges) {
        this.edges = edges;
    }

    public Map<String, Point> getLayout() {
        return layout;
    }

    public void setLayout(Map<String, Point> layout) {
        this.layout = layout;
    }

    public List<String> getRequiredInputs() {
        return requiredInputs;
    }

    public void setRequiredInputs(List<String> requiredInputs) {
        this.requiredInputs = requiredInputs == null ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(requiredInputs));
    }

    /** 当前流程使用的上下文变量生产者绑定。 */
    public List<FlowContextBinding> getContextBindings() {
        return contextBindings;
    }

    public void setContextBindings(List<FlowContextBinding> contextBindings) {
        this.contextBindings = contextBindings == null ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(contextBindings));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphDefinition)) {
            return false;
        }
        GraphDefinition that = (GraphDefinition) other;
        return schemaVersion == that.schemaVersion
                && Objects.equals(nodes, that.nodes)
                && Objects.equals(edges, that.edges)
                && Objects.equals(layout, that.layout)
                && Objects.equals(requiredInputs, that.requiredInputs)
                && Objects.equals(contextBindings, that.contextBindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, nodes, edges, layout, requiredInputs, contextBindings);
    }
}
