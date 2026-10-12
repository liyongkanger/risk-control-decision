package com.dmp.riskdecision.decision;

import com.dmp.riskdecision.policy.FlowPublicationService;
import com.dmp.riskdecision.policy.FlowVersion;
import com.dmp.riskdecision.policy.SceneFlowRepository;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.orchestrator.LiteFlowChainRegistry;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class DecisionExecutionServiceTest {
    @Autowired SceneFlowRepository repository;
    @Autowired FlowPublicationService publication;
    @Autowired DecisionExecutionService execution;

    @Test
    void executesThePublishedVersionAndReturnsNodeTrace() {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("emit", NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST,
                        Map.of("value", "accepted")),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "emit", null),
                        new GraphEdge("b", "emit", "end", null)), Map.of());
        repository.saveDraft("execute", graph, 0);
        FlowVersion published = publication.publish("execute", 1);

        ExecutionResult result = execution.evaluate("request-1", "execute", Map.of());

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.versionId()).isEqualTo(published.id());
        assertThat(result.nodeResults()).containsEntry("emit", "accepted");
        assertThat(result.trace()).extracting("nodeId").contains("emit");
    }

    @Test
    void unknownSceneDoesNotRunAProcess() {
        assertThatThrownBy(() -> execution.evaluate("request-2", "missing", Map.of()))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void pinsOneVersionEvenWhenPublishedPointerChangesDuringExecution() throws Exception {
        SceneFlowRepository storage = mock(SceneFlowRepository.class);
        LiteFlowChainRegistry chainRegistry = mock(LiteFlowChainRegistry.class);
        FlowExecutor flowExecutor = mock(FlowExecutor.class);
        LiteflowResponse response = mock(LiteflowResponse.class);
        FlowVersion first = new FlowVersion(1, "pin", 1, graphFor(1),
                "scene_pin_v1", "THEN(emitValue.tag(\"emit\"))");
        FlowVersion second = new FlowVersion(2, "pin", 2, graphFor(2),
                "scene_pin_v2", "THEN(emitValue.tag(\"emit\"))");
        AtomicReference<FlowVersion> pointer = new AtomicReference<>(first);
        when(storage.findPublished("pin")).thenAnswer(call -> Optional.of(pointer.get()));
        when(chainRegistry.contains(first.chainId())).thenReturn(true);
        when(response.isSuccess()).thenReturn(true);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        doAnswer(call -> {
            started.countDown();
            assertThat(proceed.await(5, TimeUnit.SECONDS)).isTrue();
            return response;
        }).when(flowExecutor).execute2Resp(eq(first.chainId()), isNull(), any(Object.class));
        DecisionExecutionService service = new DecisionExecutionService(storage, chainRegistry, flowExecutor);

        CompletableFuture<ExecutionResult> pending = CompletableFuture.supplyAsync(
                () -> service.evaluate("pin-request", "pin", Map.of()));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        pointer.set(second);
        proceed.countDown();

        assertThat(pending.get(5, TimeUnit.SECONDS).versionId()).isEqualTo(first.id());
        verify(storage).findPublished("pin");
    }

    private static GraphDefinition graphFor(int value) {
        return new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("emit", NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST,
                        Map.of("value", value)),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "emit", null),
                        new GraphEdge("b", "emit", "end", null)), Map.of());
    }
}
