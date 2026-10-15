package com.dmp.riskdecision.rule.model;

import java.util.List;
import lombok.Data;

/** 按优先级匹配的规则行，行内条件全部成立才命中。 */
@Data
public class RuleRow {
    private String id;
    private Integer priority;
    private List<RuleCondition> conditions;
    private RuleOutcome result;
}
