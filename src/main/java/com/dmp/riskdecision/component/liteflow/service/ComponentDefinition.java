package com.dmp.riskdecision.component.liteflow.service;

import java.util.Objects;
import java.util.List;
import java.util.Map;

/** 流程组件定义。 */
public class ComponentDefinition {
    private final String key;
    private final String liteFlowNodeId;
    private final boolean condition;
    private final String displayName;
    private final String description;
    private final String outputType;
    private final String category;
    private final List<ComponentConfigField> configFields;
    private final Map<String, Object> defaultConfig;

    public ComponentDefinition(String key, String liteFlowNodeId, boolean condition) {
        this(key, liteFlowNodeId, condition, key, "", "ANY", List.of(), Map.of(),
                condition ? "CONDITION" : "DATA");
    }

    public ComponentDefinition(String key, String liteFlowNodeId, boolean condition, String displayName,
                               String description, String outputType, List<ComponentConfigField> configFields,
                               Map<String, Object> defaultConfig, String category) {
        this.key = key;
        this.liteFlowNodeId = liteFlowNodeId;
        this.condition = condition;
        this.displayName = displayName;
        this.description = description;
        this.outputType = outputType;
        this.category = category;
        this.configFields = List.copyOf(configFields);
        this.defaultConfig = Map.copyOf(defaultConfig);
    }

    public String getKey() {
        return key;
    }

    public String getLiteFlowNodeId() {
        return liteFlowNodeId;
    }

    public boolean isCondition() {
        return condition;
    }

    public String getDisplayName() { return displayName; }

    public String getDescription() { return description; }

    public String getOutputType() { return outputType; }

    public String getCategory() { return category; }

    public List<ComponentConfigField> getConfigFields() { return configFields; }

    public Map<String, Object> getDefaultConfig() { return defaultConfig; }

    /** 按组件目录声明的必填字段和基础类型校验节点配置。 */
    public boolean acceptsConfig(Map<String, Object> config) {
        if (config == null) return false;
        for (ComponentConfigField field : configFields) {
            Object value = config.get(field.getName());
            if (value == null) {
                if (field.isRequired()) return false;
                continue;
            }
            if ("STRING".equals(field.getType()) && !(value instanceof String)
                    || "NUMBER".equals(field.getType()) && !(value instanceof Number)
                    || "OBJECT".equals(field.getType()) && !(value instanceof Map)) return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ComponentDefinition)) {
            return false;
        }
        ComponentDefinition that = (ComponentDefinition) other;
        return Objects.equals(key, that.key)
                && Objects.equals(liteFlowNodeId, that.liteFlowNodeId)
                && condition == that.condition
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(description, that.description)
                && Objects.equals(outputType, that.outputType)
                && Objects.equals(category, that.category)
                && Objects.equals(configFields, that.configFields)
                && Objects.equals(defaultConfig, that.defaultConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, liteFlowNodeId, condition, displayName, description, outputType, category,
                configFields, defaultConfig);
    }
}
