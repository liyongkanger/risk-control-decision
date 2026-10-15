package com.dmp.riskdecision.policy.service.impl;

import com.dmp.riskdecision.component.liteflow.service.ComponentCatalogService;
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
import com.dmp.riskdecision.policy.model.FlowValidation;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.repository.SceneFlowRepository;
import com.dmp.riskdecision.policy.service.FlowPublicationService;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** 流程发布用例实现，负责校验、编译、加载和发布指针切换。 */
@Service
public class FlowPublicationServiceImpl implements FlowPublicationService {
    private static final Logger LOG = LoggerFactory.getLogger(FlowPublicationServiceImpl.class);

    @Resource
    private SceneFlowRepository repository;
    @Resource
    private GraphValidator validator;
    @Resource
    private ResultPathValidator resultValidation;
    @Resource
    private SubflowReferenceValidator subflowValidation;
    @Resource
    private LiteFlowCompilationService compiler;
    @Resource
    private FlowComponentValidationService componentValidation;
    @Resource
    private LiteFlowChainService chains;
    @Resource
    private LiteFlowExecutionService execution;

    public FlowPublicationServiceImpl() {
    }

    public FlowPublicationServiceImpl(SceneFlowRepository repository, GraphValidator validator,
                                      LiteFlowCompilationService compiler, ComponentCatalogService components,
                                      LiteFlowChainService chains, LiteFlowExecutionService execution) {
        this.repository = repository;
        this.validator = validator;
        this.resultValidation = new ResultPathValidator();
        this.compiler = compiler;
        this.componentValidation = new FlowComponentValidationService(components);
        this.chains = chains;
        this.execution = execution;
    }

    /** 草稿校验通过后保存新修订号。 */
    @Override
    public FlowDraft saveDraft(String sceneCode, GraphDefinition graph, long expectedRevision) {
        repository.requireActiveScene(sceneCode);
        GraphValidationResult result = validator.validateDraft(graph);
        if (!result.isValid()) {
            throw new GraphCompilationException(result.getIssues());
        }
        return repository.saveDraft(sceneCode, graph, expectedRevision);
    }

    /** 根据场景码读取当前草稿。 */
    @Override
    public FlowDraft getDraft(String sceneCode) {
        repository.requireActiveScene(sceneCode);
        return repository.findDraft(sceneCode)
                .orElseThrow(() -> new NoSuchElementException("Draft not found: " + sceneCode));
    }

    /**
     * 发布草稿并切换场景的当前版本指针。
     *
     * <p>先生成不可变版本并加载执行链，验证成功后才切换指针；
     * 旧版本仍可供已经选中它的请求执行。步骤失败时不切换发布指针。</p>
     *
     * @see FlowPublicationService#publish(String, long)
     */
    @Override
    public FlowVersion publish(String sceneCode, long expectedRevision) {
        LOG.info("[FlowPublicationServiceImpl][publish] sceneCode:{} expectedRevision:{}",
                sceneCode, expectedRevision);
        repository.requireActiveScene(sceneCode);
        FlowDraft draft = repository.findDraft(sceneCode)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));
        if (draft.getRevision() != expectedRevision) throw new IllegalStateException("Draft revision conflict");
        GraphDefinition graph = draft.getGraph();
        GraphValidationResult result = validator.validateForPublish(graph);
        if (!result.isValid()) throw new GraphCompilationException(result.getIssues());
        componentValidation.requireValid(graph);
        GraphValidationResult resultPaths = resultValidation.validate(graph);
        if (!resultPaths.isValid()) throw new GraphCompilationException(resultPaths.getIssues());
        String flowCode = repository.resolveFlowCode(sceneCode);
        if (hasSubflow(graph)) {
            GraphValidationResult references = subflowValidation.validate(flowCode, graph);
            if (!references.isValid()) throw new GraphCompilationException(references.getIssues());
        }
        long number = repository.nextVersionNumber(sceneCode);
        CompiledFlow compiled = compiler.compile(flowCode, number, graph);
        Long previous = repository.findPublished(sceneCode).map(FlowVersion::getId).orElse(null);
        FlowVersion version = repository.createVersion(sceneCode, number, graph, compiled.getChainId(), compiled.getEl());
        chains.load(compiled.getChainId(), compiled.getEl());
        if (!hasSubflow(graph) && !hasExternalComponent(graph)) {
            verifyWithDemoInput(sceneCode, number, graph, compiled.getChainId());
        }
        if (hasSubflow(graph)) {
            GraphValidationResult references = subflowValidation.validate(flowCode, graph);
            if (!references.isValid()) throw new GraphCompilationException(references.getIssues());
        }
        repository.switchPublished(sceneCode, previous, version.getId(), expectedRevision,
                SubflowReferenceValidator.pinnedVersions(graph));
        LOG.info("[FlowPublicationServiceImpl][publish] sceneCode:{} versionId:{} published",
                sceneCode, version.getId());
        return version;
    }

    @Override
    public FlowValidation validate(String sceneCode, GraphDefinition graph) {
        repository.requireActiveScene(sceneCode);
        GraphValidationResult base = validator.validateForPublish(graph);
        if (!base.isValid()) return new FlowValidation(false, base.getIssues(), null);
        try {
            componentValidation.requireValid(graph);
            GraphValidationResult resultPaths = resultValidation.validate(graph);
            if (!resultPaths.isValid()) return new FlowValidation(false, resultPaths.getIssues(), null);
            if (hasSubflow(graph)) {
                GraphValidationResult references = subflowValidation.validate(repository.resolveFlowCode(sceneCode), graph);
                if (!references.isValid()) return new FlowValidation(false, references.getIssues(), null);
            }
            String el = compiler.compile(repository.resolveFlowCode(sceneCode), 1, graph).getEl();
            return new FlowValidation(true, List.of(), el);
        } catch (GraphCompilationException error) {
            return new FlowValidation(false, error.getIssues(), null);
        }
    }

    private void verifyWithDemoInput(String sceneCode, long number, GraphDefinition graph, String chainId) {
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
        LiteFlowRunResult result = execution.execute(chainId, "publication-check", sceneCode,
                number, input, configs);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Generated LiteFlow chain failed validation", result.getCause());
        }
    }

    /** 外部查询节点不参与发布时的演示输入执行。 */
    private static boolean hasExternalComponent(GraphDefinition graph) {
        return graph.getNodes().stream().anyMatch(node -> "get-variable".equals(node.getComponentKey())
                || "execute-rule-set".equals(node.getComponentKey()));
    }

    private static boolean hasSubflow(GraphDefinition graph) {
        return graph.getNodes().stream().anyMatch(node -> node.getKind() == NodeKind.SUBFLOW);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadPublishedAtStartup() {
        for (FlowVersion version : repository.findAllPublished()) {
            chains.load(version.getChainId(), version.getCompiledEl());
        }
    }
}
