package com.dmp.riskdecision.shared.web;

import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import java.util.List;

/** 接口错误响应，保留流程图问题的定位信息。 */
public class ApiErrorResp {
    private String code;
    private String message;
    private List<GraphIssue> issues;

    public ApiErrorResp() {
    }

    public ApiErrorResp(String code, String message, List<GraphIssue> issues) {
        this.code = code;
        this.message = message;
        this.issues = issues;
    }

    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public List<GraphIssue> getIssues() {
        return issues;
    }
    public void setIssues(List<GraphIssue> issues) {
        this.issues = issues;
    }
}
