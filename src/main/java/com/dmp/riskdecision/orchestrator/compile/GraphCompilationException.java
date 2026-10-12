package com.dmp.riskdecision.orchestrator.compile;

import com.dmp.riskdecision.orchestrator.graph.GraphIssue;

import java.util.List;

public class GraphCompilationException extends IllegalArgumentException {
    private final List<GraphIssue> issues;

    public GraphCompilationException(List<GraphIssue> issues) {
        super(issues.isEmpty() ? "Graph cannot be compiled" : issues.getFirst().message());
        this.issues = List.copyOf(issues);
    }

    public List<GraphIssue> issues() {
        return issues;
    }
}
