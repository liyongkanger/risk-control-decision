package com.dmp.riskdecision.orchestrator.graph;

import java.util.List;

public record GraphValidationResult(List<GraphIssue> issues) {
    public boolean valid() {
        return issues.isEmpty();
    }
}
