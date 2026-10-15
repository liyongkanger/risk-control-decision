package com.dmp.riskdecision.rule.model;

import java.util.List;
import lombok.Data;

/** 一套可发布的规则表，声明输入、允许的结果码、优先级行和兜底结果。 */
@Data
public class RuleDefinition {
    private List<RuleInput> inputs;
    private List<String> resultCodes;
    private List<RuleRow> rows;
    private RuleOutcome defaultResult;
}
