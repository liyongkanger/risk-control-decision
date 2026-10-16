package com.dmp.riskdecision.rule;

import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.FlowContextBinding;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import com.dmp.riskdecision.rule.service.RuleSetService;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.Resource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
    @Resource private VariableService variables;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void executesPinnedRuleVersionAndBranchesOnResultCode() {
        String suffix = Long.toString(System.nanoTime());
        String code = "eligibility" + suffix;
        String flowCode = "ruleFlow" + suffix;
        String sceneCode = "ruleScene" + suffix;
        String variableCode = "amount" + suffix;
        VariableVersion variable = variables.create(variableCode, "规则金额", VariableValueType.DECIMAL,
                "REQUEST_INPUT", null, 0, List.of(), null);
        rules.create(code, "资格规则", definition(100, variable));
        RuleSetVersion first = rules.publish(code, 1);
        GraphDefinition graph = graph(code, first.getId(), variableCode);
        flows.create(flowCode, "规则流程");
        assertThat(flows.validate(flowCode, graph).isValid()).isTrue();
        flows.saveDraft(flowCode, graph, 0);
        flows.publish(flowCode, 1);
        scenes.create(sceneCode, "规则场景", "规则决策");
        scenes.bindFlow(sceneCode, flowCode);
        ExecutionResult denied = decisions.evaluate("rule-one", sceneCode, Map.of(variableCode, 80));
        assertThat(denied.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(denied.getBusinessResult().getConclusion()).isEqualTo("INELIGIBLE");
        rules.updateDraft(code, 1, definition(50, variable));
        rules.publish(code, 2);
        assertThat(decisions.evaluate("rule-two", sceneCode, Map.of(variableCode, 80))
                .getBusinessResult().getConclusion()).isEqualTo("INELIGIBLE");
        assertThat(flows.validate(flowCode, graph).isValid()).isFalse();
    }

    @Test
    void readsOneFlowContextVariableFromAnUpstreamScalarResult() {
        String suffix = Long.toString(System.nanoTime());
        String variableCode = "score" + suffix;
        VariableVersion variable = variables.create(variableCode, "流程风险分", VariableValueType.DECIMAL,
                "FLOW_CONTEXT", null, 0, List.of(), null);
        String ruleCode = "contextRule" + suffix;
        rules.create(ruleCode, "上下文规则", definition(100, variable));
        RuleSetVersion rule = rules.publish(ruleCode, 1);
        GraphDefinition graph = graph(ruleCode, rule.getId(), variableCode);
        graph.setRequiredInputs(List.of());
        List<GraphNode> nodes = new ArrayList<>(graph.getNodes());
        nodes.add(node("producer", NodeKind.COMPONENT, "emit-value", Map.of("value", 80)));
        graph.setNodes(nodes);
        List<GraphEdge> edges = new ArrayList<>(graph.getEdges());
        edges.removeIf(edge -> "a".equals(edge.getId()));
        edges.add(edge("a", "start", "producer", null));
        edges.add(edge("h", "producer", "rules", null));
        graph.setEdges(edges);
        graph.setContextBindings(List.of(new FlowContextBinding(variableCode, variable.getId(), "producer", "")));
        String flowCode = "contextFlow" + suffix;
        String sceneCode = "contextScene" + suffix;
        flows.create(flowCode, "上下文流程");
        assertThat(flows.validate(flowCode, graph).isValid()).isTrue();
        flows.saveDraft(flowCode, graph, 0);
        flows.publish(flowCode, 1);
        scenes.create(sceneCode, "上下文场景", "验证流程变量");
        scenes.bindFlow(sceneCode, flowCode);

        ExecutionResult result = decisions.evaluate("context-request", sceneCode, Map.of());
        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getBusinessResult().getConclusion()).isEqualTo("INELIGIBLE");
    }

    @Test
    void rejectsAFlowMissingAnExternalVariablesRequestDependency() {
        String suffix = Long.toString(System.nanoTime());
        String locatorCode = "locator" + suffix;
        VariableVersion locator = variables.create(locatorCode, "请求定位", VariableValueType.STRING,
                "REQUEST_INPUT", null, 0, List.of(), null);
        String externalCode = "remoteAmount" + suffix;
        VariableVersion external = variables.create(externalCode, "外部金额", VariableValueType.DECIMAL,
                "HTTP_GET", "http://localhost:1/value", 1000, List.of(locatorCode), "value",
                Map.of(locatorCode, locator.getId()));
        String ruleCode = "externalRule" + suffix;
        rules.create(ruleCode, "外部规则", definition(100, external));
        RuleSetVersion rule = rules.publish(ruleCode, 1);
        String flowCode = "externalFlow" + suffix;
        flows.create(flowCode, "外部变量流程");
        GraphDefinition graph = graph(ruleCode, rule.getId(), externalCode);
        graph.setRequiredInputs(List.of());

        assertThat(flows.validate(flowCode, graph).isValid()).isFalse();
    }

    @Test
    void resolvesOneSharedExternalVariableOnlyOncePerRuleEvaluation() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer remote = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        remote.createContext("/amount", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"value\":10}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        remote.start();
        try {
            String suffix = Long.toString(System.nanoTime());
            String locatorCode = "locator" + suffix;
            VariableVersion locator = variables.create(locatorCode, "请求定位", VariableValueType.STRING,
                    "REQUEST_INPUT", null, 0, List.of(), null);
            String externalCode = "sharedAmount" + suffix;
            VariableVersion external = variables.create(externalCode, "共享外部金额", VariableValueType.DECIMAL,
                    "HTTP_GET", "http://localhost:" + remote.getAddress().getPort() + "/amount",
                    1000, List.of(locatorCode), "value", Map.of(locatorCode, locator.getId()));
            String definitionJson = "{\"inputs\":["
                    + "{\"name\":\"leftAmount\",\"type\":\"DECIMAL\",\"variableBinding\":{\"variableCode\":\""
                    + externalCode + "\",\"variableVersionId\":" + external.getId() + "}},"
                    + "{\"name\":\"rightAmount\",\"type\":\"DECIMAL\",\"variableBinding\":{\"variableCode\":\""
                    + externalCode + "\",\"variableVersionId\":" + external.getId() + "}}],"
                    + "\"resultCodes\":[\"OK\"],\"rows\":[{\"id\":\"match\",\"priority\":1,"
                    + "\"conditions\":[{\"input\":\"leftAmount\",\"operator\":\"GTE\",\"expected\":10}],"
                    + "\"result\":{\"resultCode\":\"OK\",\"reasonCode\":\"MATCH\",\"reason\":\"匹配\"}}],"
                    + "\"defaultResult\":{\"resultCode\":\"OK\",\"reasonCode\":\"DEFAULT\",\"reason\":\"默认\"}}";
            String ruleCode = "sharedRule" + suffix;
            rules.create(ruleCode, "共享变量规则", json.readValue(definitionJson, RuleDefinition.class));
            RuleSetVersion published = rules.publish(ruleCode, 1);

            rules.evaluateVersion(published.getId(), Map.of(locatorCode, "u1"));

            assertThat(calls.get()).isEqualTo(1);
        } finally { remote.stop(0); }
    }

    private RuleDefinition definition(int threshold, VariableVersion variable) {
        String source = "{\"inputs\":[{\"name\":\"amount\",\"type\":\"DECIMAL\","
                + "\"variableBinding\":{\"variableCode\":\"" + variable.getVariableCode() + "\","
                + "\"variableVersionId\":" + variable.getId() + "}}],"
                + "\"resultCodes\":[\"ELIGIBLE\",\"INELIGIBLE\"],"
                + "\"rows\":[{\"id\":\"min-order\",\"priority\":10,\"conditions\":[{\"input\":\"amount\","
                + "\"operator\":\"LT\",\"expected\":" + threshold + "}],\"result\":{\"resultCode\":\"INELIGIBLE\","
                + "\"reasonCode\":\"ORDER_LOW\",\"reason\":\"金额不足\"}}],"
                + "\"defaultResult\":{\"resultCode\":\"ELIGIBLE\",\"reasonCode\":\"QUALIFIED\",\"reason\":\"符合资格\"}}";
        return json.readValue(source, RuleDefinition.class);
    }

    private GraphDefinition graph(String code, long id, String variableCode) {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                node("start", NodeKind.START, null, Map.of()),
                node("rules", NodeKind.COMPONENT, "execute-rule-set", Map.of("ruleSetCode", code,
                        "ruleVersionId", id)),
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
        graph.setRequiredInputs(List.of(variableCode));
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
