package com.dmp.riskdecision.component.liteflow.node.demo;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.component.liteflow.context.ExecutionContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeBooleanComponent;
import java.time.Instant;
import java.util.Map;

/** 按已执行节点结果中的明确字段选择条件分支。 */
@LiteflowComponent("equalsValue")
public class EqualsValueConditionAdapter extends NodeBooleanComponent {
    @Override
    public boolean processBoolean() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        String nodeId = getTag();
        Instant started = Instant.now();
        try {
            Map<String, Object> config = context.getNodeConfigs().get(nodeId);
            Object value = context.getNodeResults().get(config.get("sourceNodeId"));
            for (String key : ((String) config.get("path")).split("\\.")) {
                if (!(value instanceof Map)) throw new IllegalStateException("Condition source path is missing");
                value = ((Map<?, ?>) value).get(key);
            }
            if (value == null) throw new IllegalStateException("Condition source value is missing");
            boolean answer = config.get("expected").equals(value);
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "SUCCEEDED", null));
            return answer;
        } catch (RuntimeException error) {
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "FAILED", "CONDITION_ERROR"));
            throw error;
        }
    }
}
