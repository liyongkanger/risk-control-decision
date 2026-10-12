package com.dmp.riskdecision.component.liteflow.service;

import java.util.Objects;

/** 流程组件定义。 */
public class ComponentDefinition {
    private final String key;
    private final String liteFlowNodeId;
    private final boolean condition;

    public ComponentDefinition(String key, String liteFlowNodeId, boolean condition) {
        this.key = key;
        this.liteFlowNodeId = liteFlowNodeId;
        this.condition = condition;
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
                && condition == that.condition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, liteFlowNodeId, condition);
    }
}
