package com.dmp.riskdecision.orchestrator.graph;

import java.util.List;
import java.util.Objects;

/** 流程图校验结果。 */
public class GraphValidationResult {
    private final List<GraphIssue> issues;

    public GraphValidationResult(List<GraphIssue> issues) {
        this.issues = issues;
    }

    public List<GraphIssue> getIssues() {
        return issues;
    }

    public boolean isValid() {
        return issues.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GraphValidationResult)) {
            return false;
        }
        GraphValidationResult that = (GraphValidationResult) other;
        return Objects.equals(issues, that.issues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issues);
    }
}
