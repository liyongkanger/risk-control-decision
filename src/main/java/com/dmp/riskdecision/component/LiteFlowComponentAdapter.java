package com.dmp.riskdecision.component;

import com.dmp.riskdecision.audit.NodeTrace;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

import java.time.Instant;
import java.util.Map;

@LiteflowComponent("emitValue")
public class LiteFlowComponentAdapter extends NodeComponent {
    @Override
    public void process() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        String nodeId = getTag();
        Instant started = Instant.now();
        try {
            Map<String, Object> config = context.nodeConfigs().get(nodeId);
            if (config == null || !config.containsKey("value")) {
                throw new IllegalArgumentException("Missing value for node " + nodeId);
            }
            context.nodeResults().put(nodeId, config.get("value"));
            context.traces().add(new NodeTrace(context.requestId(), context.sceneCode(), context.version(),
                    nodeId, started, Instant.now(), "SUCCEEDED", null));
        } catch (RuntimeException error) {
            context.traces().add(new NodeTrace(context.requestId(), context.sceneCode(), context.version(),
                    nodeId, started, Instant.now(), "FAILED", "COMPONENT_ERROR"));
            throw error;
        }
    }
}
