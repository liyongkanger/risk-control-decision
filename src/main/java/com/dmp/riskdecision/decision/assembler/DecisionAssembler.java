package com.dmp.riskdecision.decision.assembler;

import com.dmp.riskdecision.decision.model.BusinessResult;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.model.SuggestedAction;
import com.dmp.riskdecision.decision.vo.resp.BusinessResultResp;
import com.dmp.riskdecision.decision.vo.resp.EvaluateDecisionResp;
import com.dmp.riskdecision.decision.vo.resp.SuggestedActionResp;
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
        response.setBusinessResult(toBusinessResultResp(result.getBusinessResult()));
        response.setNodeResults(result.getNodeResults());
        response.setTrace(result.getTrace());
        response.setErrorCode(result.getErrorCode());
        return response;
    }

    private BusinessResultResp toBusinessResultResp(BusinessResult result) {
        if (result == null) {
            return null;
        }
        BusinessResultResp response = new BusinessResultResp();
        response.setConclusion(result.getConclusion());
        response.setReasonCode(result.getReasonCode());
        response.setReason(result.getReason());
        response.setDetails(result.getDetails());
        response.setSuggestedAction(toSuggestedActionResp(result.getSuggestedAction()));
        return response;
    }

    private SuggestedActionResp toSuggestedActionResp(SuggestedAction action) {
        if (action == null) {
            return null;
        }
        SuggestedActionResp response = new SuggestedActionResp();
        response.setCode(action.getCode());
        response.setParameters(action.getParameters());
        return response;
    }
}
