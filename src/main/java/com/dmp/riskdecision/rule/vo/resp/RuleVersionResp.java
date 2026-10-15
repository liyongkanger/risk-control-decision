package com.dmp.riskdecision.rule.vo.resp;

import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import lombok.Getter;

/** 不可变规则集版本的后台响应。 */
@Getter
public class RuleVersionResp {
    private final long id;
    private final String code;
    private final long versionNumber;
    private final RuleDefinition definition;

    public RuleVersionResp(RuleSetVersion source) {
        id = source.getId();
        code = source.getCode();
        versionNumber = source.getVersionNumber();
        definition = source.getDefinition();
    }
}
