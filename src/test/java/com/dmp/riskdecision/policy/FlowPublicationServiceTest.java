package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.orchestrator.LiteFlowChainRegistry;
import com.dmp.riskdecision.orchestrator.compile.FlowCompiler;
import com.dmp.riskdecision.orchestrator.graph.GraphValidator;
import com.dmp.riskdecision.component.ComponentRegistry;
import com.yomahub.liteflow.core.FlowExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@SpringBootTest
class FlowPublicationServiceTest {
    @Autowired SceneFlowRepository repository;
    @Autowired FlowPublicationService publication;
    @Autowired LiteFlowChainRegistry chains;

    @Test
    void savesDraftWithRevisionAndPublishesImmutableVersion() {
        var first = repository.saveDraft("payment", simpleGraph(1), 0);
        assertThat(first.revision()).isEqualTo(1);
        assertThatThrownBy(() -> repository.saveDraft("payment", simpleGraph(2), 0))
                .isInstanceOf(IllegalStateException.class);

        var published = publication.publish("payment", 1);
        assertThat(published.versionNumber()).isEqualTo(1);
        assertThat(repository.findPublished("payment").orElseThrow().id()).isEqualTo(published.id());

        repository.saveDraft("payment", simpleGraph(2), 1);
        var second = publication.publish("payment", 2);
        assertThat(second.id()).isNotEqualTo(published.id());
        assertThat(repository.findVersion(published.id()).orElseThrow().graph().nodes().get(1).config())
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
        com.yomahub.liteflow.flow.FlowBus.removeChain(version.chainId());

        publication.loadPublishedAtStartup();

        assertThat(chains.contains(version.chainId())).isTrue();
    }

    @Test
    void failedChainLoadNeverSwitchesPublishedPointer() {
        SceneFlowRepository storage = mock(SceneFlowRepository.class);
        LiteFlowChainRegistry failingChains = mock(LiteFlowChainRegistry.class);
        ComponentRegistry registry = new ComponentRegistry();
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
        FlowPublicationService service = new FlowPublicationService(storage, new GraphValidator(),
                new FlowCompiler(registry), registry, failingChains, mock(FlowExecutor.class));

        assertThatThrownBy(() -> service.publish("failure", 1))
                .isInstanceOf(IllegalStateException.class).hasMessage("load failed");
        var order = inOrder(storage, failingChains);
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

        assertThatThrownBy(() -> repository.switchPublished("revisionRace", null, candidate.id(), 1))
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
