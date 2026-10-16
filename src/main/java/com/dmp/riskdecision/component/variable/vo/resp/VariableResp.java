package com.dmp.riskdecision.component.variable.vo.resp;

import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** 变量中心后台展示的当前或固定版本。 */
@Getter
public class VariableResp {
    private final long id;
    private final String variableCode;
    private final String variableName;
    private final String categoryCode;
    private final long versionNumber;
    private final VariableValueType valueType;
    private final String sourceType;
    private final String url;
    private final int timeoutMs;
    private final List<String> parameterNames;
    private final Map<String, Long> dependencyVersionIds;
    private final String resultPath;

    public VariableResp(VariableVersion version) {
        id = version.getId();
        variableCode = version.getVariableCode();
        variableName = version.getVariableName();
        categoryCode = version.getCategoryCode();
        versionNumber = version.getVersionNumber();
        valueType = version.getValueType();
        sourceType = version.getSourceType();
        url = version.getUrl();
        timeoutMs = version.getTimeoutMs();
        parameterNames = version.getParameterNames();
        dependencyVersionIds = version.getDependencyVersionIds();
        resultPath = version.getResultPath();
    }
}
