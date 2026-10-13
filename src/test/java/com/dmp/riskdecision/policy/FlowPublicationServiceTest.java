package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.component.liteflow.registry.ComponentRegistry;
import com.dmp.riskdecision.component.liteflow.service.ComponentCatalogService;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowChainService;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowExecutionService;
import com.dmp.riskdecision.component.liteflow.compile.FlowCompiler;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowCompilationService;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.GraphValidator;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.repository.SceneFlowRepository;
import com.dmp.riskdecision.policy.service.impl.FlowPublicationServiceImpl;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 草稿修订与流程发布行为验证。 */
@SpringBootTest
class FlowPublicationServiceTest {
    @Resource
    private SceneFlowRepository repository;
    @Resource
    private FlowPublicationServiceImpl publication;
    @Resource
    private LiteFlowChainService chains;

    @Test
    void savesDraftWithRevisionAndPublishesImmutableVersion() {
        FlowDraft first = repository.saveDraft("payment", simpleGraph(1), 0);
        assertThat(first.getRevision()).isEqualTo(1);
        assertThatThrownBy(() -> repository.saveDraft("payment", simpleGraph(2), 0))
                .isInstanceOf(IllegalStateException.class);

        FlowVersion published = publication.publish("payment", 1);
        assertThat(published.getVersionNumber()).isEqualTo(1);
        assertThat(repository.findPublished("payment").orElseThrow().getId()).isEqualTo(published.getId());

        repository.saveDraft("payment", simpleGraph(2), 1);
        FlowVersion second = publication.publish("payment", 2);
        assertThat(second.getId()).isNotEqualTo(published.getId());
        assertThat(repository.findVersion(published.getId()).orElseThrow().getGraph().getNodes().get(1).getConfig())
                .containsEntry("value", 1);
    }

    @Test
    void rejectsInvalidComponentConfigWithoutChangingPublishedPointer() {
        repository.saveDraft("invalidConfig", new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("emit", NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST, Map.of()),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "emit", null),
                        new GraphEdge("b", "emit", "end", null)), Map.of()), 0);

        assertThatThrownBy(() -> publication.publish("invalidConfig", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findPublished("invalidConfig")).isEmpty();
    }

    @Test
    void reloadsPublishedChainFromDatabaseAtStartup() {
        repository.saveDraft("reload", simpleGraph(9), 0);
        FlowVersion version = publication.publish("reload", 1);
        com.yomahub.liteflow.flow.FlowBus.removeChain(version.getChainId());

        publication.loadPublishedAtStartup();

        assertThat(chains.contains(version.getChainId())).isTrue();
    }

    @Test
    void failedChainLoadNeverSwitchesPublishedPointer() {
        SceneFlowRepository storage = mock(SceneFlowRepository.class);
        LiteFlowChainService failingChains = mock(LiteFlowChainService.class);
        ComponentCatalogService registry = new ComponentCatalogService(new ComponentRegistry());
        when(storage.findDraft("failure")).thenReturn(java.util.Optional.of(
                new FlowDraft("failure", simpleGraph(3), 1)));
        when(storage.nextVersionNumber("failure")).thenReturn(1L);
        when(storage.createVersion(org.mockito.ArgumentMatchers.eq("failure"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new FlowVersion(10, "failure", 1, simpleGraph(3),
                        "scene_failure_v1", "THEN(emitValue.tag(\"emit\"))"));
        doThrow(new IllegalStateException("load failed")).when(failingChains)
                .load("scene_failure_v1", "THEN(emitValue.tag(\"emit\"))");
        FlowPublicationServiceImpl service = new FlowPublicationServiceImpl(storage, new GraphValidator(),
                new LiteFlowCompilationService(new FlowCompiler(registry)), registry,
                failingChains, mock(LiteFlowExecutionService.class));

        assertThatThrownBy(() -> service.publish("failure", 1))
                .isInstanceOf(IllegalStateException.class).hasMessage("load failed");
        InOrder order = inOrder(storage, failingChains);
        order.verify(storage).createVersion(org.mockito.ArgumentMatchers.eq("failure"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        order.verify(failingChains).load("scene_failure_v1", "THEN(emitValue.tag(\"emit\"))");
        verify(storage, never()).switchPublished(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void refusesPointerSwitchWhenDraftRevisionAdvanced() {
        repository.saveDraft("revisionRace", simpleGraph(1), 0);
        FlowVersion candidate = repository.createVersion("revisionRace", 1, simpleGraph(1),
                "scene_revisionRace_v1", "THEN(emitValue.tag(\"emit\"))");
        repository.saveDraft("revisionRace", simpleGraph(2), 1);

        assertThatThrownBy(() -> repository.switchPublished("revisionRace", null, candidate.getId(), 1))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findPublished("revisionRace")).isEmpty();
    }

    static GraphDefinition simpleGraph(int value) {
        return new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("emit", NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST, Map.of("value", value)),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "emit", null),
                        new GraphEdge("b", "emit", "end", null)), Map.of());
    }
}
