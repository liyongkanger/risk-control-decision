package com.dmp.riskdecision.decision.service.impl;

import com.dmp.riskdecision.component.liteflow.service.LiteFlowChainService;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowExecutionService;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowRunResult;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.repository.SceneFlowRepository;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 已发布场景的同步决策执行实现。 */
@Service
public class DecisionExecutionServiceImpl implements DecisionExecutionService {
    private static final Logger LOG = LoggerFactory.getLogger(DecisionExecutionServiceImpl.class);

    @Resource
    private SceneFlowRepository repository;
    @Resource
    private LiteFlowChainService chains;
    @Resource
    private LiteFlowExecutionService execution;

    public DecisionExecutionServiceImpl() {
    }

    public DecisionExecutionServiceImpl(SceneFlowRepository repository, LiteFlowChainService chains,
                                        LiteFlowExecutionService execution) {
        this.repository = repository;
        this.chains = chains;
        this.execution = execution;
    }

    /**
     * 根据场景当前发布指针执行同步决策。
     *
     * <p>在执行前固定 {@link FlowVersion}，即使期间切换发布指针，
     * 本次请求仍返回所选版本的结果。</p>
     *
     * @see DecisionExecutionService#evaluate(String, String, Map)
     */
    @Override
    public ExecutionResult evaluate(String requestId, String sceneCode, Map<String, Object> input) {
        if (sceneCode == null || sceneCode.isBlank()) throw new IllegalArgumentException("sceneCode is required");
        FlowVersion version = repository.findPublished(sceneCode)
                .orElseThrow(() -> new NoSuchElementException("Published scene not found: " + sceneCode));
        if (!chains.contains(version.getChainId())) chains.load(version.getChainId(), version.getCompiledEl());
        String id = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        Map<String, Map<String, Object>> configs = new HashMap<>();
        for (GraphNode node : version.getGraph().getNodes()) {
            if (node.getKind() == NodeKind.COMPONENT || node.getKind() == NodeKind.CONDITION) {
                configs.put(node.getId(), node.getConfig());
            }
        }
        LiteFlowRunResult response = execution.execute(version.getChainId(), id, sceneCode,
                version.getVersionNumber(), input, configs);
        LOG.info("[DecisionExecutionServiceImpl][evaluate] requestId:{} sceneCode:{} versionId:{} status:{}",
                id, sceneCode, version.getId(), response.isSuccess() ? "SUCCEEDED" : "FAILED");
        return new ExecutionResult(id, sceneCode, version.getId(),
                response.isSuccess() ? "SUCCEEDED" : "FAILED",
                response.getNodeResults(), response.getTraces(),
                response.isSuccess() ? null : "FLOW_EXECUTION_FAILED");
    }
}
