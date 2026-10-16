package com.dmp.riskdecision.decision.model;

import com.dmp.riskdecision.audit.NodeTrace;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 决策执行结果。 */
public class ExecutionResult {
    private final String executionId;
    private final String requestId;
    private final String sceneCode;
    private final long versionId;
    private final String status;
    private final Map<String, Object> nodeResults;
    private final List<NodeTrace> trace;
    private final String errorCode;
    private final BusinessResult businessResult;

    public ExecutionResult(String requestId, String sceneCode, long versionId, String status, Map<String, Object> nodeResults, List<NodeTrace> trace, String errorCode) {
        this(requestId, sceneCode, versionId, status, nodeResults, trace, errorCode, null);
    }

    public ExecutionResult(String requestId, String sceneCode, long versionId, String status, Map<String, Object> nodeResults,
                           List<NodeTrace> trace, String errorCode, BusinessResult businessResult) {
        this(null, requestId, sceneCode, versionId, status, nodeResults, trace, errorCode, businessResult);
    }

    public ExecutionResult(String executionId, String requestId, String sceneCode, long versionId, String status,
                           Map<String, Object> nodeResults, List<NodeTrace> trace, String errorCode,
                           BusinessResult businessResult) {
        if ("FAILED".equals(status) && businessResult != null) {
            throw new IllegalArgumentException("failed execution cannot have a business result");
        }
        this.executionId = executionId;
        this.requestId = requestId;
        this.sceneCode = sceneCode;
        this.versionId = versionId;
        this.status = status;
        this.nodeResults = nodeResults;
        this.trace = trace;
        this.errorCode = errorCode;
        this.businessResult = businessResult;
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

    /** 保存后附上单次执行标识，不修改已有执行结果。 */
    public ExecutionResult withExecutionId(String id) {
        return new ExecutionResult(id, requestId, sceneCode, versionId, status,
                nodeResults, trace, errorCode, businessResult);
    }

    public String getExecutionId() {
        return executionId;
    }

    public BusinessResult getBusinessResult() {
        return businessResult;
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
        return Objects.equals(executionId, that.executionId)
                && Objects.equals(requestId, that.requestId)
                && Objects.equals(sceneCode, that.sceneCode)
                && versionId == that.versionId
                && Objects.equals(status, that.status)
                && Objects.equals(nodeResults, that.nodeResults)
                && Objects.equals(trace, that.trace)
                && Objects.equals(errorCode, that.errorCode)
                && Objects.equals(businessResult, that.businessResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId, requestId, sceneCode, versionId, status, nodeResults,
                trace, errorCode, businessResult);
    }
}
