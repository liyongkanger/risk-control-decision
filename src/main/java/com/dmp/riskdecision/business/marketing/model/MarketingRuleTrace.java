package com.dmp.riskdecision.business.marketing.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 单笔样板决策实际命中的资格规则、版本与原因。 */
@Getter
@AllArgsConstructor
public class MarketingRuleTrace {
    private final String ruleSetCode;
    private final Long ruleVersionId;
    private final String matchedRuleId;
    private final String ruleReasonCode;
}
