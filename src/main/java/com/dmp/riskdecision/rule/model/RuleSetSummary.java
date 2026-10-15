package com.dmp.riskdecision.rule.model;

import lombok.Getter;

/** 后台规则集列表所需的轻量元数据。 */
@Getter
public class RuleSetSummary {
    private final String code;
    private final String name;
    private final long draftRevision;
    private final Long currentVersionId;

    public RuleSetSummary(String code, String name, long draftRevision, Long currentVersionId) {
        this.code = code;
        this.name = name;
        this.draftRevision = draftRevision;
        this.currentVersionId = currentVersionId;
    }
}
