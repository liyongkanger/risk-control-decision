package com.dmp.riskdecision.decision;

import com.dmp.riskdecision.component.ExecutionContext;
import com.dmp.riskdecision.orchestrator.LiteFlowChainRegistry;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.FlowVersion;
import com.dmp.riskdecision.policy.SceneFlowRepository;
import com.yomahub.liteflow.core.FlowExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class DecisionExecutionService {
    private final SceneFlowRepository repository;
    private final LiteFlowChainRegistry chains;
    private final FlowExecutor executor;

    public DecisionExecutionService(SceneFlowRepository repository, LiteFlowChainRegistry chains, FlowExecutor executor) {
        this.repository = repository;
        this.chains = chains;
        this.executor = executor;
    }

    public ExecutionResult evaluate(String requestId, String sceneCode, Map<String, Object> input) {
        if (sceneCode == null || sceneCode.isBlank()) throw new IllegalArgumentException("sceneCode is required");
        FlowVersion version = repository.findPublished(sceneCode)
                .orElseThrow(() -> new NoSuchElementException("Published scene not found: " + sceneCode));
        if (!chains.contains(version.chainId())) chains.load(version.chainId(), version.compiledEl());
        String id = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        Map<String, Map<String, Object>> configs = new HashMap<>();
        for (GraphNode node : version.graph().nodes()) {
            if (node.kind() == NodeKind.COMPONENT || node.kind() == NodeKind.CONDITION) {
                configs.put(node.id(), node.config());
            }
        }
        ExecutionContext context = new ExecutionContext(id, sceneCode, version.versionNumber(),
                input == null ? Map.of() : Map.copyOf(input), new ConcurrentHashMap<>(),
                new ConcurrentLinkedQueue<>(), Map.copyOf(configs));
        var response = executor.execute2Resp(version.chainId(), null, context);
        return new ExecutionResult(id, sceneCode, version.id(),
                response.isSuccess() ? "SUCCEEDED" : "FAILED",
                Map.copyOf(context.nodeResults()), List.copyOf(context.traces()),
                response.isSuccess() ? null : "FLOW_EXECUTION_FAILED");
    }
}
