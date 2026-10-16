package com.dmp.riskdecision.rule.model;

import lombok.Data;

/** 规则集要求的单个强类型输入。 */
@Data
public class RuleInput {
    private String name;
    private String type;
    private RuleVariableBinding variableBinding;
}
