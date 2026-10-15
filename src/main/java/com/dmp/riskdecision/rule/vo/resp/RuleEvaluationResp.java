package com.dmp.riskdecision.rule.vo.resp;

import com.dmp.riskdecision.rule.model.RuleEvaluation;
import lombok.Getter;

/** 规则试算结论、命中行及来源版本的后台响应。 */
@Getter
public class RuleEvaluationResp {
    private final String resultCode;
    private final String reasonCode;
    private final String reason;
    private final String matchedRuleId;
    private final String ruleSetCode;
    private final long ruleVersionId;

    public RuleEvaluationResp(RuleEvaluation source) {
        resultCode = source.getResultCode();
        reasonCode = source.getReasonCode();
        reason = source.getReason();
        matchedRuleId = source.getMatchedRuleId();
        ruleSetCode = source.getRuleSetCode();
        ruleVersionId = source.getRuleVersionId();
    }
}
