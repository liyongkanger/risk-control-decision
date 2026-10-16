package com.dmp.riskdecision.component.variable.vo.req;

import com.dmp.riskdecision.component.variable.model.VariableValueType;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** 创建变量首个版本的 HTTP 请求。 */
@Data
public class CreateVariableReq {
    private String variableCode;
    private String variableName;
    private String categoryCode;
    private VariableValueType valueType;
    private String sourceType;
    private String url;
    private int timeoutMs;
    private List<String> parameterNames;
    private Map<String, Long> dependencyVersionIds;
    private String resultPath;
}
