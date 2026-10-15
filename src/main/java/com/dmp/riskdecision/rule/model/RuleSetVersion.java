package com.dmp.riskdecision.rule.model;

import lombok.Getter;

/** 发布后不可变的规则集版本与规则定义。 */
@Getter
public class RuleSetVersion {
    private final long id;
    private final String code;
    private final long versionNumber;
    private final RuleDefinition definition;

    public RuleSetVersion(long id, String code, long versionNumber, RuleDefinition definition) {
        this.id = id;
        this.code = code;
        this.versionNumber = versionNumber;
        this.definition = definition;
    }
}
