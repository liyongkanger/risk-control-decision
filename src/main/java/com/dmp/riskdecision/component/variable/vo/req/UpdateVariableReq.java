package com.dmp.riskdecision.component.variable.vo.req;

import com.dmp.riskdecision.component.variable.model.VariableValueType;
import java.util.List;
import lombok.Data;

/** 基于当前版本号创建新变量版本的 HTTP 请求。 */
@Data
public class UpdateVariableReq {
    private long expectedVersion;
    private String variableName;
    private VariableValueType valueType;
    private String sourceType;
    private String url;
    private int timeoutMs;
    private List<String> parameterNames;
    private String resultPath;
}
