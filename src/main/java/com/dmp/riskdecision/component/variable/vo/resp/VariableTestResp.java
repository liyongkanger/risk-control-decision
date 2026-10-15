package com.dmp.riskdecision.component.variable.vo.resp;

import com.dmp.riskdecision.component.variable.model.VariableValueType;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 变量当前版本试取值结果。 */
@Data
@AllArgsConstructor
public class VariableTestResp {
    private String variableCode;
    private long versionId;
    private VariableValueType valueType;
    private Object value;
}
