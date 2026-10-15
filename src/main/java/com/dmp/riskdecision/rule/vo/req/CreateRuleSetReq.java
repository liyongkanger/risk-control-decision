package com.dmp.riskdecision.rule.vo.req;

import com.dmp.riskdecision.rule.model.RuleDefinition;
import lombok.Data;

/** 创建规则集首份草稿的请求。 */
@Data
public class CreateRuleSetReq {
    private String code;
    private String name;
    private RuleDefinition definition;
}
