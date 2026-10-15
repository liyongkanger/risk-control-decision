package com.dmp.riskdecision.component.liteflow.node.variable;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.component.liteflow.context.ExecutionContext;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 按发布时固定的变量版本读取强类型单值，并写入当前节点结果。 */
@LiteflowComponent("getVariable")
public class GetVariableNodeAdapter extends NodeComponent {
    @Resource private VariableService variables;

    @Override
    public void process() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        String nodeId = getTag();
        Instant started = Instant.now();
        try {
            Map<String, Object> config = context.getNodeConfigs().get(nodeId);
            @SuppressWarnings("unchecked") Map<String, String> mappings =
                    (Map<String, String>) config.get("inputMappings");
            Map<String, Object> parameters = new HashMap<>();
            for (Map.Entry<String, String> mapping : mappings.entrySet()) {
                Object value = context.getInput().get(mapping.getValue());
                if (value == null) throw new IllegalArgumentException("Variable input is missing: " + mapping.getValue());
                parameters.put(mapping.getKey(), value);
            }
            Object value = variables.resolve(((Number) config.get("versionId")).longValue(),
                    (String) config.get("variableCode"), parameters);
            context.getNodeResults().put(nodeId, value);
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "SUCCEEDED", null));
        } catch (RuntimeException error) {
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "FAILED", "VARIABLE_RESOLUTION_FAILED"));
            throw error;
        }
    }
}
