package com.dmp.riskdecision.component.liteflow.vo.resp;

import com.dmp.riskdecision.component.liteflow.service.ComponentConfigField;
import com.dmp.riskdecision.component.liteflow.service.ComponentDefinition;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** 编辑器可用的组件类型、默认配置和字段契约。 */
@Getter
public class ComponentDefinitionResp {
    private final String key;
    private final String displayName;
    private final String description;
    private final String outputType;
    private final String category;
    private final boolean condition;
    private final List<ComponentConfigField> configFields;
    private final Map<String, Object> defaultConfig;

    public ComponentDefinitionResp(ComponentDefinition definition) {
        key = definition.getKey();
        displayName = definition.getDisplayName();
        description = definition.getDescription();
        outputType = definition.getOutputType();
        category = definition.getCategory();
        condition = definition.isCondition();
        configFields = definition.getConfigFields();
        defaultConfig = definition.getDefaultConfig();
    }
}
