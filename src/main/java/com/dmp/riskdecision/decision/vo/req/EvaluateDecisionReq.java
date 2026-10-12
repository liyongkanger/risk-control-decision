package com.dmp.riskdecision.decision.vo.req;

import java.util.Map;

/** 同步决策请求。 */
public class EvaluateDecisionReq {
    private String requestId;
    private String sceneCode;
    private Map<String, Object> input;

    public EvaluateDecisionReq() {
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public void setSceneCode(String sceneCode) {
        this.sceneCode = sceneCode;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

}
