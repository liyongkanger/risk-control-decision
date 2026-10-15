package com.dmp.riskdecision.component.liteflow.node.result;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.component.liteflow.context.ExecutionContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 将显式结果节点的配置写入单次执行结果槽。 */
@LiteflowComponent("businessResult")
public class ResultNodeAdapter extends NodeComponent {
    @Override
    public void process() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        String nodeId = getTag();
        Instant started = Instant.now();
        try {
            Map<String, Object> config = context.getNodeConfigs().get(nodeId);
            Map<String, Object> resolved = new LinkedHashMap<>(config);
            Object sourceId = resolved.remove("reasonFromRuleNodeId");
            if (sourceId != null) {
                Object raw = context.getNodeResults().get(sourceId);
                if (!(raw instanceof Map) || !(((Map<?, ?>) raw).get("reasonCode") instanceof String)
                        || !(((Map<?, ?>) raw).get("reason") instanceof String)) {
                    throw new IllegalStateException("Rule result reason is unavailable");
                }
                resolved.put("reasonCode", ((Map<?, ?>) raw).get("reasonCode"));
                resolved.put("reason", ((Map<?, ?>) raw).get("reason"));
                Map<String, Object> details = new LinkedHashMap<>();
                if (resolved.get("details") instanceof Map) {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) resolved.get("details")).entrySet()) {
                        details.put((String) entry.getKey(), entry.getValue());
                    }
                }
                Map<?, ?> ruleResult = (Map<?, ?>) raw;
                details.put("ruleSetCode", ruleResult.get("ruleSetCode"));
                details.put("ruleVersionId", ruleResult.get("ruleVersionId"));
                if (ruleResult.get("matchedRuleId") != null) {
                    details.put("matchedRuleId", ruleResult.get("matchedRuleId"));
                }
                resolved.put("details", details);
            }
            context.setBusinessResultConfig(resolved);
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "SUCCEEDED", null));
        } catch (RuntimeException error) {
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "FAILED", "RESULT_ERROR"));
            throw error;
        }
    }
}
