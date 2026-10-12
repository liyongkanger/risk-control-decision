package com.dmp.riskdecision.orchestrator.graph;

import java.util.Objects;

/** 流程图校验问题。 */
public class GraphIssue {
    private final String code;
    private final String nodeId;
    private final String edgeId;
    private final String message;

    public GraphIssue(String code, String nodeId, String edgeId, String message) {
        this.code = code;
        this.nodeId = nodeId;
        this.edgeId = edgeId;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getEdgeId() {
        return edgeId;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphIssue)) {
            return false;
        }
        GraphIssue that = (GraphIssue) other;
        return Objects.equals(code, that.code)
                && Objects.equals(nodeId, that.nodeId)
                && Objects.equals(edgeId, that.edgeId)
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, nodeId, edgeId, message);
    }
}
