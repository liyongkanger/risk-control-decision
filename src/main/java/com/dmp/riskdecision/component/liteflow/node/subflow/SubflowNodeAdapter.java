package com.dmp.riskdecision.component.liteflow.node.subflow;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.component.liteflow.context.ExecutionContext;
import com.dmp.riskdecision.component.liteflow.service.SubflowInvoker;
import com.dmp.riskdecision.component.liteflow.service.SubflowInvocationException;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import jakarta.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;

/** 按调用节点配置构造子流程输入，并隔离其执行结果。 */
@LiteflowComponent("decisionSubflow")
public class SubflowNodeAdapter extends NodeComponent {
    @Resource
    private ObjectProvider<SubflowInvoker> invoker;

    @Override
    public void process() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        String nodeId = getTag();
        Instant started = Instant.now();
        try {
            Map<String, Object> config = context.getNodeConfigs().get(nodeId);
            if (config == null) throw new IllegalStateException("Subflow configuration is missing");
            Map<String, Object> input = mapInput(config, context);
            Map<String, Object> result = invoker.getObject().invoke(
                    context.getRequestId() + ":" + nodeId,
                    (String) config.get("flowCode"), ((Number) config.get("versionId")).longValue(), input);
            context.getNodeResults().put(nodeId, result);
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "SUCCEEDED", null));
        } catch (RuntimeException error) {
            Map<String, Object> config = context.getNodeConfigs().get(nodeId);
            if (error instanceof SubflowInvocationException) {
                SubflowInvocationException failure = (SubflowInvocationException) error;
                context.getNodeResults().put(nodeId, Map.of(
                        "flowCode", failure.getFlowCode(), "versionId", failure.getVersionId(),
                        "status", "FAILED", "errorCode", failure.getErrorCode(),
                        "childTrace", failure.getChildTrace()));
            } else if (config != null && config.get("versionId") instanceof Number
                    && config.get("flowCode") instanceof String) {
                context.getNodeResults().put(nodeId, Map.of(
                        "flowCode", config.get("flowCode"),
                        "versionId", ((Number) config.get("versionId")).longValue(),
                        "status", "FAILED", "errorCode", "SUBFLOW_EXECUTION_FAILED"));
            }
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "FAILED", "SUBFLOW_EXECUTION_FAILED"));
            throw error;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapInput(Map<String, Object> config, ExecutionContext context) {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> mappings = (Map<String, Object>) config.get("inputMappings");
        for (Map.Entry<String, Object> entry : mappings.entrySet()) {
            Map<String, Object> source = (Map<String, Object>) entry.getValue();
            Object value;
            if ("REQUEST_INPUT".equals(source.get("source"))) {
                value = context.getInput().get(source.get("key"));
            } else {
                value = context.getNodeResults().get(source.get("nodeId"));
                for (String key : ((String) source.get("path")).split("\\.")) {
                    if (!(value instanceof Map)) {
                        value = null;
                        break;
                    }
                    value = ((Map<?, ?>) value).get(key);
                }
            }
            if (value == null) throw new IllegalStateException("Subflow input mapping value is missing: " + entry.getKey());
            input.put(entry.getKey(), value);
        }
        return Map.copyOf(input);
    }
}
