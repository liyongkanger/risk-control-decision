package com.dmp.riskdecision.component.variable.service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 向变量中心暴露当前应用内已接入的只读取值来源。 */
@Service
public class VariableSourceRegistry {
    @Resource private List<VariableSource> sources;

    /** 来源类型未接入时明确拒绝，不回退到其他来源。 */
    public VariableSource require(String type) {
        Map<String, VariableSource> registered = sources.stream()
                .collect(Collectors.toMap(VariableSource::sourceType, Function.identity()));
        VariableSource source = registered.get(type);
        if (source == null) throw new IllegalArgumentException("Unknown variable source type: " + type);
        return source;
    }
}
