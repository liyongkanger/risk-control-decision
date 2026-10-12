package com.dmp.riskdecision.component.liteflow.node.demo;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.component.liteflow.context.ExecutionContext;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import java.time.Instant;
import java.util.Map;

/** 将示例赋值组件接入 LiteFlow 节点。 */
@LiteflowComponent("emitValue")
public class LiteFlowComponentAdapter extends NodeComponent {
    @Override
    public void process() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        String nodeId = getTag();
        Instant started = Instant.now();
        try {
            Map<String, Object> config = context.getNodeConfigs().get(nodeId);
            if (config == null || !config.containsKey("value")) {
                throw new IllegalArgumentException("Missing value for node " + nodeId);
            }
            context.getNodeResults().put(nodeId, config.get("value"));
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(), context.getVersion(),
                    nodeId, started, Instant.now(), "SUCCEEDED", null));
        } catch (RuntimeException error) {
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(), context.getVersion(),
                    nodeId, started, Instant.now(), "FAILED", "COMPONENT_ERROR"));
            throw error;
        }
    }
}
