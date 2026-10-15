package com.dmp.riskdecision.rule;

import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import com.dmp.riskdecision.rule.service.RuleSetService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证规则节点执行、结果分支及已发布流程的规则版本隔离。 */
@SpringBootTest
class RuleFlowIntegrationTest {
    @Resource private RuleSetService rules;
    @Resource private FlowAssetService flows;
    @Resource private SceneService scenes;
    @Resource private DecisionExecutionService decisions;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void executesPinnedRuleVersionAndBranchesOnResultCode() {
        String suffix = Long.toString(System.nanoTime());
        String code = "eligibility" + suffix;
        String flowCode = "ruleFlow" + suffix;
        String sceneCode = "ruleScene" + suffix;
        rules.create(code, "资格规则", definition(100));
        RuleSetVersion first = rules.publish(code, 1);
        GraphDefinition graph = graph(code, first.getId());
        flows.create(flowCode, "规则流程");
        assertThat(flows.validate(flowCode, graph).isValid()).isTrue();
        flows.saveDraft(flowCode, graph, 0);
        flows.publish(flowCode, 1);
        scenes.create(sceneCode, "规则场景", "规则决策");
        scenes.bindFlow(sceneCode, flowCode);
        ExecutionResult denied = decisions.evaluate("rule-one", sceneCode, Map.of("amount", 80));
        assertThat(denied.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(denied.getBusinessResult().getConclusion()).isEqualTo("INELIGIBLE");
        rules.updateDraft(code, 1, definition(50));
        rules.publish(code, 2);
        assertThat(decisions.evaluate("rule-two", sceneCode, Map.of("amount", 80))
                .getBusinessResult().getConclusion()).isEqualTo("INELIGIBLE");
        assertThat(flows.validate(flowCode, graph).isValid()).isFalse();
    }

    private RuleDefinition definition(int threshold) {
        String source = "{\"inputs\":[{\"name\":\"amount\",\"type\":\"DECIMAL\"}],"
                + "\"resultCodes\":[\"ELIGIBLE\",\"INELIGIBLE\"],"
                + "\"rows\":[{\"id\":\"min-order\",\"priority\":10,\"conditions\":[{\"input\":\"amount\","
                + "\"operator\":\"LT\",\"expected\":" + threshold + "}],\"result\":{\"resultCode\":\"INELIGIBLE\","
                + "\"reasonCode\":\"ORDER_LOW\",\"reason\":\"金额不足\"}}],"
                + "\"defaultResult\":{\"resultCode\":\"ELIGIBLE\",\"reasonCode\":\"QUALIFIED\",\"reason\":\"符合资格\"}}";
        return json.readValue(source, RuleDefinition.class);
    }

    private GraphDefinition graph(String code, long id) {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                node("start", NodeKind.START, null, Map.of()),
                node("rules", NodeKind.COMPONENT, "execute-rule-set", Map.of("ruleSetCode", code,
                        "ruleVersionId", id, "inputMappings", Map.of("amount", Map.of("source", "INPUT", "key", "amount")))),
                node("check", NodeKind.CONDITION, "rule-result-equals", Map.of("sourceNodeId", "rules",
                        "expectedResultCode", "ELIGIBLE")),
                node("yes", NodeKind.RESULT, null, Map.of("conclusion", "ELIGIBLE", "reasonCode", "RULE", "reason", "通过")),
                node("no", NodeKind.RESULT, null, Map.of("conclusion", "INELIGIBLE", "reasonCode", "RULE", "reason", "拒绝")),
                node("join", NodeKind.CONDITION_JOIN, null, Map.of()),
                node("end", NodeKind.END, null, Map.of())), List.of(
                edge("a", "start", "rules", null), edge("b", "rules", "check", null),
                edge("c", "check", "yes", "true"), edge("d", "check", "no", "false"),
                edge("e", "yes", "join", null), edge("f", "no", "join", null),
                edge("g", "join", "end", null)), Map.of());
        graph.setRequiredInputs(List.of("amount"));
        return graph;
    }

    private GraphNode node(String id, NodeKind kind, String component, Map<String, Object> config) {
        return new GraphNode(id, kind, component,
                kind == NodeKind.COMPONENT || kind == NodeKind.CONDITION ? FailurePolicy.FAIL_FAST : null, config);
    }

    private GraphEdge edge(String id, String source, String target, String branch) {
        return new GraphEdge(id, source, target, branch);
    }
}
