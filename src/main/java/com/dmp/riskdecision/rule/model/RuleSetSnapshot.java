package com.dmp.riskdecision.rule.model;

import lombok.Getter;

/** 后台查看的规则集草稿及当前发布指针。 */
@Getter
public class RuleSetSnapshot {
    private final String code;
    private final String name;
    private final long draftRevision;
    private final RuleDefinition definition;
    private final Long currentVersionId;

    public RuleSetSnapshot(String code, String name, long draftRevision, RuleDefinition definition,
                           Long currentVersionId) {
        this.code = code;
        this.name = name;
        this.draftRevision = draftRevision;
        this.definition = definition;
        this.currentVersionId = currentVersionId;
    }
}
