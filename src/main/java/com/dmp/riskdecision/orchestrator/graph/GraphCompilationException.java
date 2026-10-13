package com.dmp.riskdecision.orchestrator.graph;

import java.util.List;

/** 流程图无法编译时携带结构化校验问题。 */
public class GraphCompilationException extends IllegalArgumentException {
    private final List<GraphIssue> issues;

    public GraphCompilationException(List<GraphIssue> issues) {
        super(issues.isEmpty() ? "Graph cannot be compiled" : issues.getFirst().getMessage());
        this.issues = List.copyOf(issues);
    }

    public List<GraphIssue> getIssues() {
        return issues;
    }
}
