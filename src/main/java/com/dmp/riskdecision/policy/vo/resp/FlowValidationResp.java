package com.dmp.riskdecision.policy.vo.resp;

import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import java.util.List;

/** 流程校验响应。 */
public class FlowValidationResp {
    private boolean valid;
    private List<GraphIssue> issues;
    private String compiledEl;

    public FlowValidationResp() {
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public List<GraphIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<GraphIssue> issues) {
        this.issues = issues;
    }

    public String getCompiledEl() {
        return compiledEl;
    }

    public void setCompiledEl(String compiledEl) {
        this.compiledEl = compiledEl;
    }

}
