package com.dmp.riskdecision.rule.vo.resp;

import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleSetSnapshot;
import lombok.Getter;

/** 规则集当前草稿及发布指针的后台响应。 */
@Getter
public class RuleSetResp {
    private final String code;
    private final String name;
    private final long draftRevision;
    private final RuleDefinition definition;
    private final Long currentVersionId;

    public RuleSetResp(RuleSetSnapshot source) {
        code = source.getCode();
        name = source.getName();
        draftRevision = source.getDraftRevision();
        definition = source.getDefinition();
        currentVersionId = source.getCurrentVersionId();
    }
}
