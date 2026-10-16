package com.dmp.riskdecision.component.variable.vo.resp;

import com.dmp.riskdecision.component.variable.model.VariableCategory;
import lombok.Getter;

/** 分类选择项。 */
@Getter
public class VariableCategoryResp {
    private final String code;
    private final String name;

    public VariableCategoryResp(VariableCategory category) {
        code = category.getCode();
        name = category.getDisplayName();
    }
}
