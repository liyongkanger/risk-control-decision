package com.dmp.riskdecision.policy.vo.req;

import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.Point;
import java.util.List;
import java.util.Map;

/** 流程图请求。 */
public class FlowGraphReq {
    private int schemaVersion;
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
    private Map<String, Point> layout;
    private List<String> requiredInputs;

    public FlowGraphReq() {
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
        this.requiredInputs = requiredInputs;
    }

}
