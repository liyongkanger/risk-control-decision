package com.dmp.riskdecision.policy.vo.req;

/** 流程发布请求。 */
public class PublishFlowReq {
    private long expectedRevision;

    public PublishFlowReq() {
    }

    public long getExpectedRevision() {
        return expectedRevision;
    }

    public void setExpectedRevision(long expectedRevision) {
        this.expectedRevision = expectedRevision;
    }

}
