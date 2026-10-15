package com.dmp.riskdecision.rule.model;

import lombok.Data;

/** 规则行中针对一个输入字段的比较条件。 */
@Data
public class RuleCondition {
    private String input;
    private String operator;
    private Object expected;
}
