package com.dmp.riskdecision.rule.vo.req;

import com.dmp.riskdecision.rule.model.RuleDefinition;
import lombok.Data;

/** 按预期修订号保存规则草稿的请求。 */
@Data
public class UpdateRuleDraftReq {
    private long expectedRevision;
    private RuleDefinition definition;
}
