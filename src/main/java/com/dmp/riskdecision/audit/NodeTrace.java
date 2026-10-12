package com.dmp.riskdecision.audit;

import java.time.Instant;
import java.util.Objects;

/** 节点执行轨迹。 */
public class NodeTrace {
    private final String requestId;
    private final String sceneCode;
    private final long flowVersion;
    private final String nodeId;
    private final Instant startedAt;
    private final Instant endedAt;
    private final String status;
    private final String errorCode;

    public NodeTrace(String requestId, String sceneCode, long flowVersion, String nodeId, Instant startedAt, Instant endedAt, String status, String errorCode) {
        this.requestId = requestId;
        this.sceneCode = sceneCode;
        this.flowVersion = flowVersion;
        this.nodeId = nodeId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.status = status;
        this.errorCode = errorCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public long getFlowVersion() {
        return flowVersion;
    }

    public String getNodeId() {
        return nodeId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NodeTrace)) {
            return false;
        }
        NodeTrace that = (NodeTrace) other;
        return Objects.equals(requestId, that.requestId)
                && Objects.equals(sceneCode, that.sceneCode)
                && flowVersion == that.flowVersion
                && Objects.equals(nodeId, that.nodeId)
                && Objects.equals(startedAt, that.startedAt)
                && Objects.equals(endedAt, that.endedAt)
                && Objects.equals(status, that.status)
                && Objects.equals(errorCode, that.errorCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, sceneCode, flowVersion, nodeId, startedAt, endedAt, status, errorCode);
    }
}
