package com.dmp.riskdecision.component;

import com.dmp.riskdecision.audit.NodeTrace;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeBooleanComponent;

import java.time.Instant;
import java.util.Map;

@LiteflowComponent("isPositive")
public class LiteFlowConditionAdapter extends NodeBooleanComponent {
    @Override
    public boolean processBoolean() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        String nodeId = getTag();
        Instant started = Instant.now();
        try {
            Map<String, Object> config = context.nodeConfigs().get(nodeId);
            if (config == null || !(config.get("inputKey") instanceof String key)) {
                throw new IllegalArgumentException("Missing inputKey for node " + nodeId);
            }
            Object value = context.input().get(key);
            boolean answer = value instanceof Boolean b ? b : value instanceof Number n && n.doubleValue() > 0;
            context.traces().add(new NodeTrace(nodeId, started, Instant.now(), "SUCCEEDED", null));
            return answer;
        } catch (RuntimeException error) {
            context.traces().add(new NodeTrace(nodeId, started, Instant.now(), "FAILED", "COMPONENT_ERROR"));
            throw error;
        }
    }
}
