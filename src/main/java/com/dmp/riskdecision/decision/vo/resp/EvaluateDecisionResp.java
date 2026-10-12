package com.dmp.riskdecision.decision.vo.resp;

import com.dmp.riskdecision.audit.NodeTrace;
import java.util.List;
import java.util.Map;

/** 同步决策响应。 */
public class EvaluateDecisionResp {
    private String requestId;
    private String sceneCode;
    private long versionId;
    private String status;
    private Map<String, Object> nodeResults;
    private List<NodeTrace> trace;
    private String errorCode;

    public EvaluateDecisionResp() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public long getVersionId() {
        return versionId;
    }

    public void setVersionId(long versionId) {
        this.versionId = versionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getNodeResults() {
        return nodeResults;
    }

    public void setNodeResults(Map<String, Object> nodeResults) {
        this.nodeResults = nodeResults;
    }

    public List<NodeTrace> getTrace() {
        return trace;
    }

    public void setTrace(List<NodeTrace> trace) {
        this.trace = trace;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

}
