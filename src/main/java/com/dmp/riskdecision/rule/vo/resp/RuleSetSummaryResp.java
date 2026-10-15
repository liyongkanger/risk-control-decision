package com.dmp.riskdecision.rule.vo.resp;

import com.dmp.riskdecision.rule.model.RuleSetSummary;
import lombok.Getter;

/** 规则集后台列表行，不传输完整规则表。 */
@Getter
public class RuleSetSummaryResp {
    private final String code;
    private final String name;
    private final long draftRevision;
    private final Long currentVersionId;

    public RuleSetSummaryResp(RuleSetSummary source) {
        code = source.getCode();
        name = source.getName();
        draftRevision = source.getDraftRevision();
        currentVersionId = source.getCurrentVersionId();
    }
}
