package com.dmp.riskdecision.rule.model;

import lombok.Data;

/** 规则输入对变量中心不可变版本的引用。 */
@Data
public class RuleVariableBinding {
    private String variableCode;
    private Long variableVersionId;
}
