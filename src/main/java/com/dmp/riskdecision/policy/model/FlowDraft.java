package com.dmp.riskdecision.policy.model;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import java.util.Objects;

/** 流程草稿。 */
public class FlowDraft {
    private final String sceneCode;
    private final GraphDefinition graph;
    private final long revision;

    public FlowDraft(String sceneCode, GraphDefinition graph, long revision) {
        this.sceneCode = sceneCode;
        this.graph = graph;
        this.revision = revision;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public GraphDefinition getGraph() {
        return graph;
    }

    public long getRevision() {
        return revision;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FlowDraft)) {
            return false;
        }
        FlowDraft that = (FlowDraft) other;
        return Objects.equals(sceneCode, that.sceneCode)
                && Objects.equals(graph, that.graph)
                && revision == that.revision;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sceneCode, graph, revision);
    }
}
