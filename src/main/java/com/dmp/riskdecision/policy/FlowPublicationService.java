package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.component.ComponentRegistry;
import com.dmp.riskdecision.component.ExecutionContext;
import com.dmp.riskdecision.orchestrator.LiteFlowChainRegistry;
import com.dmp.riskdecision.orchestrator.compile.FlowCompiler;
import com.dmp.riskdecision.orchestrator.compile.GraphCompilationException;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.GraphValidator;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.yomahub.liteflow.core.FlowExecutor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class FlowPublicationService {
    private final SceneFlowRepository repository;
    private final GraphValidator validator;
    private final FlowCompiler compiler;
    private final ComponentRegistry components;
    private final LiteFlowChainRegistry chains;
    private final FlowExecutor executor;

    public FlowPublicationService(SceneFlowRepository repository, GraphValidator validator,
                                  FlowCompiler compiler, ComponentRegistry components,
                                  LiteFlowChainRegistry chains, FlowExecutor executor) {
        this.repository = repository;
        this.validator = validator;
        this.compiler = compiler;
        this.components = components;
        this.chains = chains;
        this.executor = executor;
    }

    @Transactional
    public FlowVersion publish(String sceneCode, long expectedRevision) {
        FlowDraft draft = repository.findDraft(sceneCode)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));
        if (draft.revision() != expectedRevision) throw new IllegalStateException("Draft revision conflict");
        GraphDefinition graph = draft.graph();
        var result = validator.validateForPublish(graph);
        if (!result.valid()) throw new GraphCompilationException(result.issues());
        validateComponents(graph);
        long number = repository.nextVersionNumber(sceneCode);
        var compiled = compiler.compile(sceneCode, number, graph);
        chains.load(compiled.chainId(), compiled.el());
        verifyWithDemoInput(sceneCode, number, graph, compiled.chainId());
        Long previous = repository.findPublished(sceneCode).map(FlowVersion::id).orElse(null);
        FlowVersion version = repository.createVersion(sceneCode, number, graph, compiled.chainId(), compiled.el());
        repository.switchPublished(sceneCode, previous, version.id());
        return version;
    }

    private void validateComponents(GraphDefinition graph) {
        for (GraphNode node : graph.nodes()) {
            if (node.kind() != NodeKind.COMPONENT && node.kind() != NodeKind.CONDITION) continue;
            var definition = components.require(node.componentKey());
            if (definition.condition() != (node.kind() == NodeKind.CONDITION)) {
                throw new GraphCompilationException(List.of(new GraphIssue("COMPONENT_KIND_MISMATCH", node.id(), null,
                        "Component kind does not match node kind")));
            }
            Map<String, Object> config = node.config();
            if (config == null || (node.kind() == NodeKind.COMPONENT && !config.containsKey("value"))
                    || (node.kind() == NodeKind.CONDITION && !(config.get("inputKey") instanceof String))) {
                throw new GraphCompilationException(List.of(new GraphIssue("COMPONENT_CONFIG_INVALID", node.id(), null,
                        "Component configuration is invalid")));
            }
        }
    }

    private void verifyWithDemoInput(String sceneCode, long number, GraphDefinition graph, String chainId) {
        Map<String, Map<String, Object>> configs = new HashMap<>();
        Map<String, Object> input = new HashMap<>();
        for (GraphNode node : graph.nodes()) {
            if (node.kind() == NodeKind.COMPONENT || node.kind() == NodeKind.CONDITION) {
                configs.put(node.id(), node.config());
            }
            if (node.kind() == NodeKind.CONDITION) {
                input.put((String) node.config().get("inputKey"), true);
            }
        }
        ExecutionContext context = new ExecutionContext("publication-check", sceneCode, number, input,
                new ConcurrentHashMap<>(), new ConcurrentLinkedQueue<>(), configs);
        var response = executor.execute2Resp(chainId, null, context);
        if (!response.isSuccess()) throw new IllegalStateException("Generated LiteFlow chain failed validation", response.getCause());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadPublishedAtStartup() {
        for (FlowVersion version : repository.findAllPublished()) {
            chains.load(version.chainId(), version.compiledEl());
        }
    }
}
