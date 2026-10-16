package com.dmp.riskdecision.component.variable.model;

import java.util.List;
import java.util.Map;
import lombok.Data;

/** 一个不可变变量版本的配置快照。 */
@Data
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
    private Map<String, Long> dependencyVersionIds;
    private String categoryCode;

    public VariableVersion(long id, String variableCode, String variableName, long versionNumber,
                           VariableValueType valueType, String sourceType, String url, int timeoutMs,
                           List<String> parameterNames, String resultPath,
                           Map<String, Long> dependencyVersionIds) {
        this.id = id;
        this.variableCode = variableCode;
        this.variableName = variableName;
        this.versionNumber = versionNumber;
        this.valueType = valueType;
        this.sourceType = sourceType;
        this.url = url;
        this.timeoutMs = timeoutMs;
        this.parameterNames = parameterNames;
        this.resultPath = resultPath;
        this.dependencyVersionIds = dependencyVersionIds;
    }

    /** 兼容旧调用；新来源定义应明确提供固定依赖版本。 */
    public VariableVersion(long id, String variableCode, String variableName, long versionNumber,
                           VariableValueType valueType, String sourceType, String url, int timeoutMs,
                           List<String> parameterNames, String resultPath) {
        this(id, variableCode, variableName, versionNumber, valueType, sourceType, url, timeoutMs,
                parameterNames, resultPath, Map.of());
    }
}
