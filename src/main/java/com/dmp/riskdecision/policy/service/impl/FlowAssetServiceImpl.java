package com.dmp.riskdecision.policy.service.impl;

import com.dmp.riskdecision.component.liteflow.service.CompiledFlow;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowChainService;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowCompilationService;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowExecutionService;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowRunResult;
import com.dmp.riskdecision.orchestrator.graph.GraphCompilationException;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.GraphValidationResult;
import com.dmp.riskdecision.orchestrator.graph.GraphValidator;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.orchestrator.graph.ResultPathValidator;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowInfo;
import com.dmp.riskdecision.policy.model.FlowPage;
import com.dmp.riskdecision.policy.model.FlowValidation;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.repository.FlowAssetRepository;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.FlowCodeConflictException;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** 不依赖业务场景的流程资产管理与发布实现。 */
@Service
@Slf4j
public class FlowAssetServiceImpl implements FlowAssetService {
    @Resource
    private FlowAssetRepository repository;
    @Resource
    private GraphValidator validator;
    @Resource
    private ResultPathValidator resultValidation;
    @Resource
    private SubflowReferenceValidator subflowValidation;
    @Resource
    private FlowComponentValidationService componentValidation;
    @Resource
    private LiteFlowCompilationService compiler;
    @Resource
    private LiteFlowChainService chains;
    @Resource
    private LiteFlowExecutionService execution;

    @Override
    public FlowInfo create(String flowCode, String flowName) {
        log.info("[FlowAssetServiceImpl][create] flowCode:{}", flowCode);
        if (flowCode == null || !flowCode.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid flow code");
        }
        String name = flowName == null ? "" : flowName.trim();
        if (name.isEmpty() || name.length() > 128) {
            throw new IllegalArgumentException("Flow name must contain 1 to 128 characters");
        }
        try {
            return repository.create(flowCode, name);
        } catch (DuplicateKeyException error) {
            throw new FlowCodeConflictException(flowCode, error);
        }
    }

    @Override
    public FlowInfo get(String flowCode) {
        return repository.require(flowCode);
    }

    @Override
    public FlowVersion getPublished(String flowCode) {
        return repository.findPublished(flowCode)
                .orElseThrow(() -> new NoSuchElementException("Published flow not found: " + flowCode));
    }

    @Override
    public FlowPage search(String keyword, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid page or pageSize");
        }
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("Keyword is too long");
        }
        String pattern = "%" + normalized.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
        return repository.search(pattern, page, pageSize);
    }

    @Override
    public FlowDraft saveDraft(String flowCode, GraphDefinition graph, long expectedRevision) {
        log.info("[FlowAssetServiceImpl][saveDraft] flowCode:{} expectedRevision:{}", flowCode, expectedRevision);
        repository.require(flowCode);
        GraphValidationResult result = validator.validateDraft(graph);
        if (!result.isValid()) throw new GraphCompilationException(result.getIssues());
        return repository.saveDraft(flowCode, graph, expectedRevision);
    }

    @Override
    public FlowDraft getDraft(String flowCode) {
        return repository.findDraft(flowCode)
                .orElseThrow(() -> new NoSuchElementException("Draft not found: " + flowCode));
    }

    @Override
    public FlowValidation validate(String flowCode, GraphDefinition graph) {
        repository.require(flowCode);
        GraphValidationResult base = validator.validateForPublish(graph);
        if (!base.isValid()) return new FlowValidation(false, base.getIssues(), null);
        try {
            componentValidation.requireValid(graph);
            GraphValidationResult resultPaths = resultValidation.validate(graph);
            if (!resultPaths.isValid()) return new FlowValidation(false, resultPaths.getIssues(), null);
            if (hasSubflow(graph)) {
                GraphValidationResult references = subflowValidation.validate(flowCode, graph);
                if (!references.isValid()) return new FlowValidation(false, references.getIssues(), null);
            }
            String el = compiler.compile(flowCode, 1, graph).getEl();
            return new FlowValidation(true, List.of(), el);
        } catch (GraphCompilationException error) {
            return new FlowValidation(false, error.getIssues(), null);
        }
    }

    @Override
    public FlowVersion publish(String flowCode, long expectedRevision) {
        log.info("[FlowAssetServiceImpl][publish] flowCode:{} expectedRevision:{}", flowCode, expectedRevision);
        FlowDraft draft = getDraft(flowCode);
        if (draft.getRevision() != expectedRevision) throw new IllegalStateException("Draft revision conflict");
        GraphDefinition graph = draft.getGraph();
        GraphValidationResult result = validator.validateForPublish(graph);
        if (!result.isValid()) throw new GraphCompilationException(result.getIssues());
        componentValidation.requireValid(graph);
        GraphValidationResult resultPaths = resultValidation.validate(graph);
        if (!resultPaths.isValid()) throw new GraphCompilationException(resultPaths.getIssues());
        if (hasSubflow(graph)) {
            GraphValidationResult references = subflowValidation.validate(flowCode, graph);
            if (!references.isValid()) throw new GraphCompilationException(references.getIssues());
        }
        long number = repository.nextVersionNumber(flowCode);
        CompiledFlow compiled = compiler.compile(flowCode, number, graph);
        Long previous = repository.findPublished(flowCode).map(FlowVersion::getId).orElse(null);
        FlowVersion version = repository.createVersion(flowCode, number, graph,
                compiled.getChainId(), compiled.getEl());
        chains.load(compiled.getChainId(), compiled.getEl());
        if (!hasSubflow(graph)) verifyWithDemoInput(flowCode, number, graph, compiled.getChainId());
        if (hasSubflow(graph)) {
            GraphValidationResult references = subflowValidation.validate(flowCode, graph);
            if (!references.isValid()) throw new GraphCompilationException(references.getIssues());
        }
        repository.switchPublished(flowCode, previous, version.getId(), expectedRevision,
                SubflowReferenceValidator.pinnedVersions(graph));
        return version;
    }

    private void verifyWithDemoInput(String flowCode, long number, GraphDefinition graph, String chainId) {
        Map<String, Map<String, Object>> configs = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        for (GraphNode node : graph.getNodes()) {
            if (node.getKind() == NodeKind.COMPONENT || node.getKind() == NodeKind.CONDITION
                    || node.getKind() == NodeKind.RESULT || node.getKind() == NodeKind.SUBFLOW) {
                configs.put(node.getId(), node.getConfig());
            }
            if (node.getKind() == NodeKind.CONDITION) {
                input.put((String) node.getConfig().get("inputKey"), true);
            }
        }
        LiteFlowRunResult result = execution.execute(chainId, "publication-check", flowCode,
                number, input, configs);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Generated LiteFlow chain failed validation", result.getCause());
        }
    }

    private static boolean hasSubflow(GraphDefinition graph) {
        return graph.getNodes().stream().anyMatch(node -> node.getKind() == NodeKind.SUBFLOW);
    }
}
