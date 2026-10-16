package com.dmp.riskdecision.orchestrator.graph;

import java.util.Objects;

/** 当前流程将一个变量版本绑定到唯一上游节点的结果字段。 */
public class FlowContextBinding {
    private String variableCode;
    private long variableVersionId;
    private String nodeId;
    private String resultPath;

    public FlowContextBinding() { }

    public FlowContextBinding(String variableCode, long variableVersionId, String nodeId, String resultPath) {
        this.variableCode = variableCode;
        this.variableVersionId = variableVersionId;
        this.nodeId = nodeId;
        this.resultPath = resultPath;
    }

    public String getVariableCode() { return variableCode; }
    public void setVariableCode(String variableCode) { this.variableCode = variableCode; }
    public long getVariableVersionId() { return variableVersionId; }
    public void setVariableVersionId(long variableVersionId) { this.variableVersionId = variableVersionId; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public String getResultPath() { return resultPath; }
    public void setResultPath(String resultPath) { this.resultPath = resultPath; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FlowContextBinding)) return false;
        FlowContextBinding that = (FlowContextBinding) other;
        return variableVersionId == that.variableVersionId
                && Objects.equals(variableCode, that.variableCode)
                && Objects.equals(nodeId, that.nodeId)
                && Objects.equals(resultPath, that.resultPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variableCode, variableVersionId, nodeId, resultPath);
    }
}
