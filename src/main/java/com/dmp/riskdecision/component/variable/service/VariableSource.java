package com.dmp.riskdecision.component.variable.service;

import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import java.util.List;
import java.util.Map;

/** 一种受控只读取值来源，由变量中心按已登记来源类型选择。 */
public interface VariableSource {
    /** 返回变量定义中使用的稳定来源类型。 */
    String sourceType();

    /** 来源可约束业务变量码、参数和路径；默认交给变量中心的通用校验。 */
    default void validateDefinition(String code, VariableValueType valueType, String url,
                                    List<String> parameters, String resultPath) { }

    /** 按不可变变量版本及完整参数读取一个强类型单值。 */
    Object fetch(VariableVersion version, Map<String, Object> parameters);
}
