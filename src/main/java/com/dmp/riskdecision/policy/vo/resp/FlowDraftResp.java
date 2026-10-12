package com.dmp.riskdecision.policy.vo.resp;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;

/** 流程草稿响应。 */
public class FlowDraftResp {
    private String sceneCode;
    private GraphDefinition graph;
    private long revision;

    public FlowDraftResp() {
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public GraphDefinition getGraph() {
        return graph;
    }

    public void setGraph(GraphDefinition graph) {
        this.graph = graph;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

}
