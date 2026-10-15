package com.dmp.riskdecision.component.variable.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 一个不可变变量版本的配置快照。 */
@Data
@AllArgsConstructor
public class VariableVersion {
    private long id;
    private String variableCode;
    private String variableName;
    private long versionNumber;
    private VariableValueType valueType;
    private String sourceType;
    private String url;
    private int timeoutMs;
    private List<String> parameterNames;
    private String resultPath;
}
