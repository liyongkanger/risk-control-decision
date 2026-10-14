package com.dmp.riskdecision.decision.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 决策建议的后续动作，不表示动作已经执行。 */
@Getter
@EqualsAndHashCode
public class SuggestedAction {
    private final String code;
    private final Map<String, Object> parameters;

    public SuggestedAction(String code, Map<String, Object> parameters) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("suggested action code is required");
        }
        this.code = code;
        this.parameters = parameters == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }
}
