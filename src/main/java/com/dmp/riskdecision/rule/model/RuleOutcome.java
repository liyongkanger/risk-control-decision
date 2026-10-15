package com.dmp.riskdecision.rule.model;

import lombok.Data;

/** 规则行或兜底行返回的业务结论及原因。 */
@Data
public class RuleOutcome {
    private String resultCode;
    private String reasonCode;
    private String reason;
}
