package com.dmp.riskdecision.decision.model;

import com.dmp.riskdecision.audit.NodeTrace;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 决策执行结果。 */
public class ExecutionResult {
    private final String requestId;
    private final String sceneCode;
    private final long versionId;
    private final String status;
    private final Map<String, Object> nodeResults;
    private final List<NodeTrace> trace;
    private final String errorCode;

    public ExecutionResult(String requestId, String sceneCode, long versionId, String status, Map<String, Object> nodeResults, List<NodeTrace> trace, String errorCode) {
        this.requestId = requestId;
        this.sceneCode = sceneCode;
        this.versionId = versionId;
        this.status = status;
        this.nodeResults = nodeResults;
        this.trace = trace;
        this.errorCode = errorCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public long getVersionId() {
        return versionId;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getNodeResults() {
        return nodeResults;
    }

    public List<NodeTrace> getTrace() {
        return trace;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExecutionResult)) {
            return false;
        }
        ExecutionResult that = (ExecutionResult) other;
        return Objects.equals(requestId, that.requestId)
                && Objects.equals(sceneCode, that.sceneCode)
                && versionId == that.versionId
                && Objects.equals(status, that.status)
                && Objects.equals(nodeResults, that.nodeResults)
                && Objects.equals(trace, that.trace)
                && Objects.equals(errorCode, that.errorCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, sceneCode, versionId, status, nodeResults, trace, errorCode);
    }
}
