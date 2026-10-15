package com.dmp.riskdecision.component.liteflow.node.rule;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.component.liteflow.context.ExecutionContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeBooleanComponent;
import java.time.Instant;
import java.util.Map;

/** 只按上游规则结果码选择条件分支。 */
@LiteflowComponent("ruleResultEquals")
public class RuleResultEqualsNodeAdapter extends NodeBooleanComponent {
    @Override
    public boolean processBoolean() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        String nodeId = getTag();
        Instant started = Instant.now();
        try {
            Map<String, Object> config = context.getNodeConfigs().get(nodeId);
            Object source = context.getNodeResults().get(config.get("sourceNodeId"));
            if (!(source instanceof Map) || ((Map<?, ?>) source).get("resultCode") == null) {
                throw new IllegalStateException("Rule result is unavailable");
            }
            boolean answer = config.get("expectedResultCode").equals(((Map<?, ?>) source).get("resultCode"));
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "SUCCEEDED", null));
            return answer;
        } catch (RuntimeException error) {
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "FAILED", "RULE_RESULT_FAILED"));
            throw error;
        }
    }
}
