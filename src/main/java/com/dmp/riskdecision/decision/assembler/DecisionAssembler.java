package com.dmp.riskdecision.decision.assembler;

import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.vo.resp.EvaluateDecisionResp;
import org.springframework.stereotype.Component;

/** 决策执行结果到接口响应的转换。 */
@Component
public class DecisionAssembler {
    public EvaluateDecisionResp toEvaluateResp(ExecutionResult result) {
        EvaluateDecisionResp response = new EvaluateDecisionResp();
        response.setRequestId(result.getRequestId());
        response.setSceneCode(result.getSceneCode());
        response.setVersionId(result.getVersionId());
        response.setStatus(result.getStatus());
        response.setNodeResults(result.getNodeResults());
        response.setTrace(result.getTrace());
        response.setErrorCode(result.getErrorCode());
        return response;
    }
}
