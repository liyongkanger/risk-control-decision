package com.dmp.riskdecision.decision.service.impl;

import com.dmp.riskdecision.component.liteflow.service.LiteFlowChainService;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowExecutionService;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowRunResult;
import com.dmp.riskdecision.decision.model.BusinessResult;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.model.SuggestedAction;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.decision.service.DecisionRecordService;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.FlowContextBinding;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.repository.SceneFlowRepository;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
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
    @Resource
    private DecisionRecordService records;

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
        if (requestId != null && requestId.length() > 191) {
            throw new IllegalArgumentException("requestId is too long");
        }
        FlowVersion version = repository.findPublished(sceneCode)
                .orElseThrow(() -> new NoSuchElementException("Published scene not found: " + sceneCode));
        ExecutionResult result = evaluateVersion(requestId, sceneCode, version, input);
        return records == null ? result : result.withExecutionId(records.save(result, input));
    }

    /** 按已固定的不可变版本运行流程，供子流程调用复用。 */
    public ExecutionResult evaluateVersion(String requestId, String sceneCode, FlowVersion version,
                                           Map<String, Object> input) {
        if (!chains.contains(version.getChainId())) chains.load(version.getChainId(), version.getCompiledEl());
        String id = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        Map<String, Map<String, Object>> configs = new HashMap<>();
        for (GraphNode node : version.getGraph().getNodes()) {
            if (node.getKind() == NodeKind.COMPONENT || node.getKind() == NodeKind.CONDITION
                    || node.getKind() == NodeKind.RESULT || node.getKind() == NodeKind.SUBFLOW) {
                configs.put(node.getId(), node.getConfig());
            }
        }
        LiteFlowRunResult response = execution.execute(version.getChainId(), id, sceneCode,
                version.getVersionNumber(), input, configs,
                version.getGraph().getContextBindings().stream().collect(Collectors.toMap(
                        FlowContextBinding::getVariableCode, binding -> binding)));
        boolean missingResult = response.isSuccess() && response.getBusinessResultConfig() == null
                && version.getGraph().getNodes().stream().anyMatch(node -> node.getKind() == NodeKind.RESULT);
        boolean succeeded = response.isSuccess() && !missingResult;
        BusinessResult businessResult = succeeded && response.getBusinessResultConfig() != null
                ? toBusinessResult(response.getBusinessResultConfig()) : null;
        LOG.info("[DecisionExecutionServiceImpl][evaluate] requestId:{} sceneCode:{} versionId:{} status:{}",
                id, sceneCode, version.getId(), succeeded ? "SUCCEEDED" : "FAILED");
        return new ExecutionResult(id, sceneCode, version.getId(),
                succeeded ? "SUCCEEDED" : "FAILED",
                response.getNodeResults(), response.getTraces(),
                succeeded ? null : missingResult ? "BUSINESS_RESULT_MISSING" : "FLOW_EXECUTION_FAILED",
                businessResult);
    }

    @SuppressWarnings("unchecked")
    private static BusinessResult toBusinessResult(Map<String, Object> config) {
        Map<String, Object> details = config.get("details") instanceof Map
                ? (Map<String, Object>) config.get("details") : Map.of();
        SuggestedAction action = null;
        if (config.get("suggestedAction") instanceof Map) {
            Map<String, Object> actionConfig = (Map<String, Object>) config.get("suggestedAction");
            Map<String, Object> parameters = actionConfig.get("parameters") instanceof Map
                    ? (Map<String, Object>) actionConfig.get("parameters") : Map.of();
            action = new SuggestedAction((String) actionConfig.get("code"), parameters);
        }
        return new BusinessResult((String) config.get("conclusion"), (String) config.get("reasonCode"),
                (String) config.get("reason"), details, action);
    }
}
