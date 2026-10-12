package com.dmp.riskdecision.component.liteflow.service;

import com.dmp.riskdecision.audit.NodeTrace;
import java.util.List;
import java.util.Map;

/** 单次 LiteFlow 执行结果及节点轨迹。 */
public class LiteFlowRunResult {
    private final boolean success;
    private final Throwable cause;
    private final Map<String, Object> nodeResults;
    private final List<NodeTrace> traces;

    public LiteFlowRunResult(boolean success, Throwable cause,
                             Map<String, Object> nodeResults, List<NodeTrace> traces) {
        this.success = success;
        this.cause = cause;
        this.nodeResults = nodeResults;
        this.traces = traces;
    }

    public boolean isSuccess() {
        return success;
    }
    public Throwable getCause() {
        return cause;
    }
    public Map<String, Object> getNodeResults() {
        return nodeResults;
    }
    public List<NodeTrace> getTraces() {
        return traces;
    }
}
