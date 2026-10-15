package com.dmp.riskdecision.rule.model;

import lombok.Getter;

/** 一次规则计算的业务结果与固定版本追溯信息。 */
@Getter
public class RuleEvaluation {
    private final String resultCode;
    private final String reasonCode;
    private final String reason;
    private final String matchedRuleId;
    private final String ruleSetCode;
    private final long ruleVersionId;

    public RuleEvaluation(RuleOutcome outcome, String matchedRuleId, String ruleSetCode, long ruleVersionId) {
        this.resultCode = outcome.getResultCode();
        this.reasonCode = outcome.getReasonCode();
        this.reason = outcome.getReason();
        this.matchedRuleId = matchedRuleId;
        this.ruleSetCode = ruleSetCode;
        this.ruleVersionId = ruleVersionId;
    }
}
