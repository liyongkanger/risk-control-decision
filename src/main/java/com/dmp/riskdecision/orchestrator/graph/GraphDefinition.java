package com.dmp.riskdecision.orchestrator.graph;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 流程图定义。 */
public class GraphDefinition {
    private int schemaVersion;
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
    private Map<String, Point> layout;

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
                && Objects.equals(layout, that.layout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, nodes, edges, layout);
    }
}
