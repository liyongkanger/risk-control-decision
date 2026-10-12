package com.dmp.riskdecision.policy.model;

import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import java.util.List;
import java.util.Objects;

/** 流程校验结果。 */
public class FlowValidation {
    private final boolean valid;
    private final List<GraphIssue> issues;
    private final String compiledEl;

    public FlowValidation(boolean valid, List<GraphIssue> issues, String compiledEl) {
        this.valid = valid;
        this.issues = issues;
        this.compiledEl = compiledEl;
    }

    public boolean isValid() {
        return valid;
    }

    public List<GraphIssue> getIssues() {
        return issues;
    }

    public String getCompiledEl() {
        return compiledEl;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FlowValidation)) {
            return false;
        }
        FlowValidation that = (FlowValidation) other;
        return valid == that.valid
                && Objects.equals(issues, that.issues)
                && Objects.equals(compiledEl, that.compiledEl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valid, issues, compiledEl);
    }
}
