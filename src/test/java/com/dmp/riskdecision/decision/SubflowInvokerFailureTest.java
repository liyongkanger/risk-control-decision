package com.dmp.riskdecision.decision;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.component.liteflow.service.SubflowInvocationException;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.impl.DecisionExecutionServiceImpl;
import com.dmp.riskdecision.decision.service.impl.SubflowInvokerImpl;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.repository.FlowAssetRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 子流程失败时保留其内部节点位置。 */
class SubflowInvokerFailureTest {
    @Test
    void carriesChildFailureTraceToTheOuterCall() {
        FlowAssetRepository repository = mock(FlowAssetRepository.class);
        DecisionExecutionServiceImpl decisions = mock(DecisionExecutionServiceImpl.class);
        FlowVersion version = new FlowVersion(41, "child", 1,
                new GraphDefinition(1, List.of(), List.of(), Map.of()), "flow_child_v1", "EL");
        NodeTrace trace = new NodeTrace("request:call", "child", 1, "failingRule",
                Instant.EPOCH, Instant.EPOCH, "FAILED", "COMPONENT_ERROR");
        when(repository.findVersion(41)).thenReturn(Optional.of(version));
        when(decisions.evaluateVersion("request:call", "child", version, Map.of()))
                .thenReturn(new ExecutionResult("request:call", "child", 41, "FAILED",
                        Map.of(), List.of(trace), "FLOW_EXECUTION_FAILED"));
        SubflowInvokerImpl invoker = new SubflowInvokerImpl();
        ReflectionTestUtils.setField(invoker, "repository", repository);
        ReflectionTestUtils.setField(invoker, "decisions", decisions);

        assertThatThrownBy(() -> invoker.invoke("request:call", "child", 41, Map.of()))
                .isInstanceOf(SubflowInvocationException.class)
                .satisfies(error -> {
                    SubflowInvocationException failure = (SubflowInvocationException) error;
                    assertThat(failure.getVersionId()).isEqualTo(41);
                    assertThat(failure.getChildTrace()).extracting("nodeId").contains("failingRule");
                });
    }
}
