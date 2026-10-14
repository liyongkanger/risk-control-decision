package com.dmp.riskdecision.component.liteflow.registry;

import com.dmp.riskdecision.component.liteflow.service.ComponentDefinition;
import java.util.Map;
import org.springframework.stereotype.Component;

/** LiteFlow 示例组件的内部注册表。 */
@Component
public class ComponentRegistry {
    // todo 后续优化不要用代码的方式写入map

    private static final Map<String, ComponentDefinition> DEFINITIONS = Map.of(
            "emit-value", new ComponentDefinition("emit-value", "emitValue", false),
            "is-positive", new ComponentDefinition("is-positive", "isPositive", true),
            "equals-value", new ComponentDefinition("equals-value", "equalsValue", true));

    public ComponentDefinition require(String componentKey) {
        ComponentDefinition definition = DEFINITIONS.get(componentKey);
        if (definition == null) throw new IllegalArgumentException("Unknown component: " + componentKey);
        return definition;
    }
}
