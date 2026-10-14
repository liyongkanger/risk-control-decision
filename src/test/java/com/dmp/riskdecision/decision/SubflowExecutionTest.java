package com.dmp.riskdecision.decision;

import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import com.dmp.riskdecision.orchestrator.graph.GraphCompilationException;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 子流程固定版本调用与结果隔离的真实执行验证。 */
@SpringBootTest
class SubflowExecutionTest {
    @Resource private FlowAssetService flows;
    @Resource private SceneService scenes;
    @Resource private DecisionExecutionService decisions;

    @Test
    void outerFlowUsesPinnedChildVersionAndKeepsItsOwnConclusion() {
        String suffix = Long.toString(System.nanoTime());
        String childCode = "offerChild" + suffix;
        String outerCode = "offerOuter" + suffix;
        String sceneCode = "offerScene" + suffix;
        flows.create(childCode, "优惠子流程");
        flows.saveDraft(childCode, childGraph("LOW_VALUE_OFFER"), 0);
        FlowVersion childV1 = flows.publish(childCode, 1);

        flows.create(outerCode, "结算外层流程");
        flows.saveDraft(outerCode, outerGraph(childCode, childV1.getId()), 0);
        flows.publish(outerCode, 1);
        scenes.create(sceneCode, "结算场景", null);
        scenes.bindFlow(sceneCode, outerCode);

        ExecutionResult first = decisions.evaluate("checkout-1", sceneCode, Map.of());
        assertThat(first.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(first.getBusinessResult().getConclusion()).isEqualTo("FINAL_OFFER");
        Map<?, ?> call = (Map<?, ?>) first.getNodeResults().get("call");
        assertThat(call.get("versionId")).isEqualTo(childV1.getId());
        assertThat(((Map<?, ?>) call.get("businessResult")).get("conclusion"))
                .isEqualTo("LOW_VALUE_OFFER");

        flows.saveDraft(childCode, childGraph("HIGH_VALUE_OFFER"), 1);
        FlowVersion childV2 = flows.publish(childCode, 2);
        assertThat(childV2.getId()).isNotEqualTo(childV1.getId());
        ExecutionResult pinned = decisions.evaluate("checkout-2", sceneCode, Map.of());
        Map<?, ?> pinnedCall = (Map<?, ?>) pinned.getNodeResults().get("call");
        assertThat(pinnedCall.get("versionId")).isEqualTo(childV1.getId());
        assertThat(((Map<?, ?>) pinnedCall.get("businessResult")).get("conclusion"))
                .isEqualTo("LOW_VALUE_OFFER");

        com.yomahub.liteflow.flow.FlowBus.removeChain(childV1.getChainId());
        ExecutionResult reloaded = decisions.evaluate("checkout-3", sceneCode, Map.of());
        assertThat(((Map<?, ?>) ((Map<?, ?>) reloaded.getNodeResults().get("call"))
                .get("businessResult")).get("conclusion")).isEqualTo("LOW_VALUE_OFFER");

        String staleOuter = "staleOuter" + suffix;
        flows.create(staleOuter, "过期引用外层");
        flows.saveDraft(staleOuter, outerGraph(childCode, childV1.getId()), 0);
        assertThat(flows.validate(staleOuter, outerGraph(childCode, childV1.getId())).getIssues())
                .extracting("code").contains("SUBFLOW_VERSION_UNAVAILABLE");
        assertThatThrownBy(() -> flows.publish(staleOuter, 1))
                .isInstanceOf(GraphCompilationException.class);

        flows.saveDraft(outerCode, outerGraph(childCode, childV2.getId()), 1);
        flows.publish(outerCode, 2);
        scenes.bindFlow(sceneCode, outerCode);
        ExecutionResult upgraded = decisions.evaluate("checkout-4", sceneCode, Map.of());
        Map<?, ?> upgradedCall = (Map<?, ?>) upgraded.getNodeResults().get("call");
        assertThat(upgradedCall.get("versionId")).isEqualTo(childV2.getId());
        assertThat(((Map<?, ?>) upgradedCall.get("businessResult")).get("conclusion"))
                .isEqualTo("HIGH_VALUE_OFFER");
    }

    @Test
    void twoCallsMapDifferentOuterInputsAndKeepTheirResultsSeparate() {
        String suffix = Long.toString(System.nanoTime());
        String childCode = "inputChild" + suffix;
        String outerCode = "inputOuter" + suffix;
        String sceneCode = "inputScene" + suffix;
        flows.create(childCode, "输入判断子流程");
        GraphDefinition child = conditionalChildGraph();
        flows.saveDraft(childCode, child, 0);
        FlowVersion version = flows.publish(childCode, 1);
        flows.create(outerCode, "双调用外层");
        flows.saveDraft(outerCode, twoCallGraph(childCode, version.getId()), 0);
        flows.publish(outerCode, 1);
        scenes.create(sceneCode, "双调用场景", null);
        scenes.bindFlow(sceneCode, outerCode);

        ExecutionResult result = decisions.evaluate("two-calls", sceneCode,
                Map.of("leftPositive", true, "rightPositive", false, "unused", "must-not-pass"));
        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getBusinessResult().getConclusion()).isEqualTo("FINAL_OFFER");
        Map<?, ?> left = (Map<?, ?>) result.getNodeResults().get("leftCall");
        Map<?, ?> right = (Map<?, ?>) result.getNodeResults().get("rightCall");
        assertThat(((Map<?, ?>) left.get("businessResult")).get("conclusion")).isEqualTo("ELIGIBLE");
        assertThat(((Map<?, ?>) right.get("businessResult")).get("conclusion")).isEqualTo("NOT_ELIGIBLE");

        ExecutionResult outerNoOffer = decisions.evaluate("two-calls-no-offer", sceneCode,
                Map.of("leftPositive", false, "rightPositive", true));
        assertThat(outerNoOffer.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(outerNoOffer.getBusinessResult().getConclusion()).isEqualTo("NO_NEW_OFFER");

        ExecutionResult failed = decisions.evaluate("missing-input", sceneCode,
                Map.of("leftPositive", true));
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getBusinessResult()).isNull();
        assertThat(failed.getTrace()).extracting("nodeId").contains("rightCall");
        Map<?, ?> failedCall = (Map<?, ?>) failed.getNodeResults().get("rightCall");
        assertThat(failedCall.get("versionId")).isEqualTo(version.getId());
        assertThat(failedCall.get("status")).isEqualTo("FAILED");
    }

    @Test
    void laterCallCanMapAFieldFromAnEarlierCallResult() {
        String suffix = Long.toString(System.nanoTime());
        String firstCode = "firstChild" + suffix;
        String secondCode = "secondChild" + suffix;
        String outerCode = "mappedOuter" + suffix;
        String sceneCode = "mappedScene" + suffix;
        flows.create(firstCode, "第一子流程");
        GraphDefinition first = childGraph("HAS_QUALIFICATION");
        first.getNodes().stream().filter(node -> node.getKind() == NodeKind.RESULT).findFirst().orElseThrow()
                .setConfig(Map.of("conclusion", "HAS_QUALIFICATION", "reasonCode", "YES",
                        "reason", "有资格", "details", Map.of("positive", true)));
        flows.saveDraft(firstCode, first, 0);
        long firstVersion = flows.publish(firstCode, 1).getId();
        flows.create(secondCode, "第二子流程");
        flows.saveDraft(secondCode, conditionalChildGraph(), 0);
        long secondVersion = flows.publish(secondCode, 1).getId();

        GraphDefinition outer = new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("firstCall", NodeKind.SUBFLOW, null, FailurePolicy.FAIL_FAST,
                        Map.of("flowCode", firstCode, "versionId", firstVersion, "inputMappings", Map.of())),
                new GraphNode("secondCall", NodeKind.SUBFLOW, null, FailurePolicy.FAIL_FAST,
                        Map.of("flowCode", secondCode, "versionId", secondVersion,
                                "inputMappings", Map.of("positive", Map.of("source", "NODE_RESULT",
                                        "nodeId", "firstCall", "path", "businessResult.details.positive")))),
                new GraphNode("result", NodeKind.RESULT, null, null,
                        Map.of("conclusion", "FINAL", "reasonCode", "FINAL", "reason", "外层结论")),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "firstCall", null),
                        new GraphEdge("b", "firstCall", "secondCall", null),
                        new GraphEdge("c", "secondCall", "result", null),
                        new GraphEdge("d", "result", "end", null)), Map.of());
        flows.create(outerCode, "映射外层流程");
        flows.saveDraft(outerCode, outer, 0);
        flows.publish(outerCode, 1);
        scenes.create(sceneCode, "映射场景", null);
        scenes.bindFlow(sceneCode, outerCode);

        ExecutionResult execution = decisions.evaluate("mapped-request", sceneCode, Map.of());
        assertThat(execution.getStatus()).isEqualTo("SUCCEEDED");
        Map<?, ?> secondCall = (Map<?, ?>) execution.getNodeResults().get("secondCall");
        assertThat(((Map<?, ?>) secondCall.get("businessResult")).get("conclusion"))
                .isEqualTo("ELIGIBLE");
        assertThat(execution.getBusinessResult().getConclusion()).isEqualTo("FINAL");
    }

    private static GraphDefinition childGraph(String conclusion) {
        return new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("result", NodeKind.RESULT, null, null,
                        Map.of("conclusion", conclusion, "reasonCode", "DEMO", "reason", "演示结论")),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "result", null),
                        new GraphEdge("b", "result", "end", null)), Map.of());
    }

    private static GraphDefinition outerGraph(String childCode, long versionId) {
        return new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("call", NodeKind.SUBFLOW, null, FailurePolicy.FAIL_FAST,
                        Map.of("flowCode", childCode, "versionId", versionId, "inputMappings", Map.of())),
                new GraphNode("result", NodeKind.RESULT, null, null,
                        Map.of("conclusion", "FINAL_OFFER", "reasonCode", "OUTER", "reason", "外层决定")),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "call", null),
                        new GraphEdge("b", "call", "result", null),
                        new GraphEdge("c", "result", "end", null)), Map.of());
    }

    private static GraphDefinition conditionalChildGraph() {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("condition", NodeKind.CONDITION, "is-positive", FailurePolicy.FAIL_FAST,
                        Map.of("inputKey", "positive")),
                new GraphNode("yes", NodeKind.RESULT, null, null,
                        Map.of("conclusion", "ELIGIBLE", "reasonCode", "YES", "reason", "有资格")),
                new GraphNode("no", NodeKind.RESULT, null, null,
                        Map.of("conclusion", "NOT_ELIGIBLE", "reasonCode", "NO", "reason", "无资格")),
                new GraphNode("join", NodeKind.CONDITION_JOIN, null, null, Map.of()),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "condition", null),
                        new GraphEdge("b", "condition", "yes", "true"),
                        new GraphEdge("c", "condition", "no", "false"),
                        new GraphEdge("d", "yes", "join", null),
                        new GraphEdge("e", "no", "join", null),
                        new GraphEdge("f", "join", "end", null)), Map.of());
        graph.setRequiredInputs(List.of("positive"));
        return graph;
    }

    private static GraphDefinition twoCallGraph(String childCode, long versionId) {
        return new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                call("leftCall", childCode, versionId, "leftPositive"),
                call("rightCall", childCode, versionId, "rightPositive"),
                new GraphNode("choose", NodeKind.CONDITION, "equals-value", FailurePolicy.FAIL_FAST,
                        Map.of("sourceNodeId", "leftCall", "path", "businessResult.conclusion",
                                "expected", "ELIGIBLE")),
                new GraphNode("result", NodeKind.RESULT, null, null,
                        Map.of("conclusion", "FINAL_OFFER", "reasonCode", "OUTER", "reason", "外层决定")),
                new GraphNode("noResult", NodeKind.RESULT, null, null,
                        Map.of("conclusion", "NO_NEW_OFFER", "reasonCode", "OUTER_NO", "reason", "外层不推荐")),
                new GraphNode("join", NodeKind.CONDITION_JOIN, null, null, Map.of()),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "leftCall", null),
                        new GraphEdge("b", "leftCall", "rightCall", null),
                        new GraphEdge("c", "rightCall", "choose", null),
                        new GraphEdge("d", "choose", "result", "true"),
                        new GraphEdge("e", "choose", "noResult", "false"),
                        new GraphEdge("f", "result", "join", null),
                        new GraphEdge("g", "noResult", "join", null),
                        new GraphEdge("h", "join", "end", null)), Map.of());
    }

    private static GraphNode call(String id, String childCode, long versionId, String inputKey) {
        return new GraphNode(id, NodeKind.SUBFLOW, null, FailurePolicy.FAIL_FAST,
                Map.of("flowCode", childCode, "versionId", versionId,
                        "inputMappings", Map.of("positive", Map.of("source", "REQUEST_INPUT", "key", inputKey))));
    }
}
