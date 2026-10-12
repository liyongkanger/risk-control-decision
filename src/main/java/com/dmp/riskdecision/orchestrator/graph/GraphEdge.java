package com.dmp.riskdecision.orchestrator.graph;

import java.util.Objects;

/** 流程图连线。 */
public class GraphEdge {
    private String id;
    private String source;
    private String target;
    private String branch;

    public GraphEdge() {
    }

    public GraphEdge(String id, String source, String target, String branch) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.branch = branch;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphEdge)) {
            return false;
        }
        GraphEdge that = (GraphEdge) other;
        return Objects.equals(id, that.id)
                && Objects.equals(source, that.source)
                && Objects.equals(target, that.target)
                && Objects.equals(branch, that.branch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, source, target, branch);
    }
}
