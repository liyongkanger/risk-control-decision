package com.dmp.riskdecision.component.liteflow.service;

import com.dmp.riskdecision.component.liteflow.context.ExecutionContext;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Service;

/** 隔离 LiteFlow 执行器与业务用例之间的框架细节。 */
@Service
public class LiteFlowExecutionService {
    @Resource
    private FlowExecutor executor;

    public LiteFlowRunResult execute(String chainId, String requestId, String sceneCode, long version,
                                     Map<String, Object> input,
                                     Map<String, Map<String, Object>> nodeConfigs) {
        ExecutionContext context = new ExecutionContext(requestId, sceneCode, version,
                input == null ? Map.of() : Map.copyOf(input), new ConcurrentHashMap<>(),
                new ConcurrentLinkedQueue<>(), Map.copyOf(nodeConfigs));
        LiteflowResponse response = executor.execute2Resp(chainId, null, context);
        return new LiteFlowRunResult(response.isSuccess(), response.getCause(),
                Map.copyOf(context.getNodeResults()), List.copyOf(context.getTraces()));
    }
}
