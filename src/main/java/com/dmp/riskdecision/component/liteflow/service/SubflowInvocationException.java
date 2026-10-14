package com.dmp.riskdecision.component.liteflow.service;

import com.dmp.riskdecision.audit.NodeTrace;
import java.util.List;
import lombok.Getter;

/** 子流程执行失败，保留固定版本和内部失败轨迹。 */
@Getter
public class SubflowInvocationException extends RuntimeException {
    private final String flowCode;
    private final long versionId;
    private final String errorCode;
    private final List<NodeTrace> childTrace;

    public SubflowInvocationException(String flowCode, long versionId, String errorCode,
                                      List<NodeTrace> childTrace) {
        super("Subflow execution failed: " + flowCode + " version " + versionId);
        this.flowCode = flowCode;
        this.versionId = versionId;
        this.errorCode = errorCode == null ? "SUBFLOW_EXECUTION_FAILED" : errorCode;
        this.childTrace = childTrace == null ? List.of() : List.copyOf(childTrace);
    }
}
