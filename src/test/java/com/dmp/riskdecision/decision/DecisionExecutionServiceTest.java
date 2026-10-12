package com.dmp.riskdecision.decision;

import com.dmp.riskdecision.component.liteflow.service.LiteFlowChainService;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowExecutionService;
import com.dmp.riskdecision.component.liteflow.service.LiteFlowRunResult;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.decision.service.impl.DecisionExecutionServiceImpl;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.repository.SceneFlowRepository;
import com.dmp.riskdecision.policy.service.FlowPublicationService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 决策执行与版本固定行为验证。 */
@SpringBootTest
class DecisionExecutionServiceTest {
    @Resource
    private SceneFlowRepository repository;
    @Resource
    private FlowPublicationService publication;
    @Resource
    private DecisionExecutionService execution;

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

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getVersionId()).isEqualTo(published.getId());
        assertThat(result.getNodeResults()).containsEntry("emit", "accepted");
        assertThat(result.getTrace()).extracting("nodeId").contains("emit");
        assertThat(result.getTrace().getFirst().getRequestId()).isEqualTo("request-1");
        assertThat(result.getTrace().getFirst().getSceneCode()).isEqualTo("execute");
        assertThat(result.getTrace().getFirst().getFlowVersion()).isEqualTo(1);
    }

    @Test
    void unknownSceneDoesNotRunAProcess() {
        assertThatThrownBy(() -> execution.evaluate("request-2", "missing", Map.of()))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void parallelJoinRunsTheSharedSuccessorOnce() {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("split", NodeKind.PARALLEL_SPLIT, null, null, Map.of()),
                valueNode("left", 1), valueNode("right", 2),
                new GraphNode("join", NodeKind.PARALLEL_JOIN, null, null, Map.of()),
                valueNode("after", 3),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "split", null),
                        new GraphEdge("b", "split", "left", null),
                        new GraphEdge("c", "split", "right", null),
                        new GraphEdge("d", "left", "join", null),
                        new GraphEdge("e", "right", "join", null),
                        new GraphEdge("f", "join", "after", null),
                        new GraphEdge("g", "after", "end", null)), Map.of());
        repository.saveDraft("parallel", graph, 0);
        publication.publish("parallel", 1);

        ExecutionResult result = execution.evaluate("parallel-request", "parallel", Map.of());

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getNodeResults()).containsEntry("left", 1).containsEntry("right", 2).containsEntry("after", 3);
        assertThat(result.getTrace().stream().filter(item -> item.getNodeId().equals("after"))).hasSize(1);
    }

    @Test
    void pinsOneVersionEvenWhenPublishedPointerChangesDuringExecution() throws Exception {
        SceneFlowRepository storage = mock(SceneFlowRepository.class);
        LiteFlowChainService chainRegistry = mock(LiteFlowChainService.class);
        LiteFlowExecutionService flowExecutor = mock(LiteFlowExecutionService.class);
        LiteFlowRunResult response = new LiteFlowRunResult(true, null, Map.of(), List.of());
        FlowVersion first = new FlowVersion(1, "pin", 1, graphFor(1),
                "scene_pin_v1", "THEN(emitValue.tag(\"emit\"))");
        FlowVersion second = new FlowVersion(2, "pin", 2, graphFor(2),
                "scene_pin_v2", "THEN(emitValue.tag(\"emit\"))");
        AtomicReference<FlowVersion> pointer = new AtomicReference<>(first);
        when(storage.findPublished("pin")).thenAnswer(call -> Optional.of(pointer.get()));
        when(chainRegistry.contains(first.getChainId())).thenReturn(true);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        doAnswer(call -> {
            started.countDown();
            assertThat(proceed.await(5, TimeUnit.SECONDS)).isTrue();
            return response;
        }).when(flowExecutor).execute(eq(first.getChainId()), eq("pin-request"), eq("pin"),
                eq(1L), any(Map.class), any(Map.class));
        DecisionExecutionService service = new DecisionExecutionServiceImpl(storage, chainRegistry, flowExecutor);

        CompletableFuture<ExecutionResult> pending = CompletableFuture.supplyAsync(
                () -> service.evaluate("pin-request", "pin", Map.of()));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        pointer.set(second);
        proceed.countDown();

        assertThat(pending.get(5, TimeUnit.SECONDS).getVersionId()).isEqualTo(first.getId());
        verify(storage).findPublished("pin");
    }

    @Test
    void returnsFailedStatusWhenLiteFlowStopsOnComponentError() {
        SceneFlowRepository storage = mock(SceneFlowRepository.class);
        LiteFlowChainService chainRegistry = mock(LiteFlowChainService.class);
        LiteFlowExecutionService flowExecutor = mock(LiteFlowExecutionService.class);
        LiteFlowRunResult failed = new LiteFlowRunResult(false, null, Map.of(), List.of());
        FlowVersion version = new FlowVersion(7, "failureScene", 2, graphFor(1),
                "scene_failureScene_v2", "THEN(emitValue.tag(\"emit\"))");
        when(storage.findPublished("failureScene")).thenReturn(Optional.of(version));
        when(chainRegistry.contains(version.getChainId())).thenReturn(true);
        when(flowExecutor.execute(eq(version.getChainId()), eq("failed-request"), eq("failureScene"),
                eq(2L), any(Map.class), any(Map.class))).thenReturn(failed);

        ExecutionResult result = new DecisionExecutionServiceImpl(storage, chainRegistry, flowExecutor)
                .evaluate("failed-request", "failureScene", Map.of());

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorCode()).isEqualTo("FLOW_EXECUTION_FAILED");
        assertThat(result.getVersionId()).isEqualTo(7);
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

    private static GraphNode valueNode(String id, int value) {
        return new GraphNode(id, NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST, Map.of("value", value));
    }
}
