package com.dmp.riskdecision.component;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ComponentRegistry {
    private static final Map<String, ComponentDefinition> DEFINITIONS = Map.of(
            "emit-value", new ComponentDefinition("emit-value", "emitValue", false),
            "is-positive", new ComponentDefinition("is-positive", "isPositive", true));

    public ComponentDefinition require(String componentKey) {
        ComponentDefinition definition = DEFINITIONS.get(componentKey);
        if (definition == null) throw new IllegalArgumentException("Unknown component: " + componentKey);
        return definition;
    }
}
