package com.dmp.riskdecision.component.liteflow.node.demo;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.component.liteflow.context.ExecutionContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeBooleanComponent;
import java.time.Instant;
import java.util.Map;

/** 将示例条件组件接入 LiteFlow 节点。 */
@LiteflowComponent("isPositive")
public class LiteFlowConditionAdapter extends NodeBooleanComponent {
    @Override
    public boolean processBoolean() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        String nodeId = getTag();
        Instant started = Instant.now();
        try {
            Map<String, Object> config = context.getNodeConfigs().get(nodeId);
            if (config == null || !(config.get("inputKey") instanceof String)) {
                throw new IllegalArgumentException("Missing inputKey for node " + nodeId);
            }
            String key = (String) config.get("inputKey");
            Object value = context.getInput().get(key);
            boolean answer = value instanceof Boolean ? (Boolean) value
                    : value instanceof Number && ((Number) value).doubleValue() > 0;
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(), context.getVersion(),
                    nodeId, started, Instant.now(), "SUCCEEDED", null));
            return answer;
        } catch (RuntimeException error) {
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(), context.getVersion(),
                    nodeId, started, Instant.now(), "FAILED", "COMPONENT_ERROR"));
            throw error;
        }
    }
}
