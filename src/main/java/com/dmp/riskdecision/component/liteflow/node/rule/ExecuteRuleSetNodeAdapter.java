package com.dmp.riskdecision.component.liteflow.node.rule;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.component.liteflow.context.ExecutionContext;
import com.dmp.riskdecision.rule.model.RuleEvaluation;
import com.dmp.riskdecision.rule.service.RuleSetService;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** 将已发布规则集版本应用于当前流程输入，并保存可追溯结果。 */
@LiteflowComponent("executeRuleSet")
public class ExecuteRuleSetNodeAdapter extends NodeComponent {
    @Resource private RuleSetService rules;

    @Override
    public void process() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        String nodeId = getTag();
        Instant started = Instant.now();
        try {
            Map<String, Object> config = context.getNodeConfigs().get(nodeId);
            Map<?, ?> mappings = (Map<?, ?>) config.get("inputMappings");
            Map<String, Object> input = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mappings.entrySet()) {
                Map<?, ?> source = (Map<?, ?>) entry.getValue();
                Object value;
                if ("INPUT".equals(source.get("source"))) {
                    value = context.getInput().get(source.get("key"));
                } else {
                    value = context.getNodeResults().get(source.get("nodeId"));
                }
                if (value == null) throw new IllegalArgumentException("Rule input is missing: " + entry.getKey());
                input.put((String) entry.getKey(), value);
            }
            RuleEvaluation result = rules.evaluateVersion(((Number) config.get("ruleVersionId")).longValue(), input);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("resultCode", result.getResultCode());
            output.put("reasonCode", result.getReasonCode());
            output.put("reason", result.getReason());
            output.put("matchedRuleId", result.getMatchedRuleId());
            output.put("ruleSetCode", result.getRuleSetCode());
            output.put("ruleVersionId", result.getRuleVersionId());
            context.getNodeResults().put(nodeId, output);
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "SUCCEEDED", null));
        } catch (RuntimeException error) {
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "FAILED", "RULE_EXECUTION_FAILED"));
            throw error;
        }
    }
}
