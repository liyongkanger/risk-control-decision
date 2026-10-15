package com.dmp.riskdecision.component.variable;

import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.Resource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** 获取变量、精确数值比较、发布固定版本和取值失败的集成验收。 */
@SpringBootTest
class VariableFlowIntegrationTest {
    @Resource private VariableService variables;
    @Resource private FlowAssetService flows;
    @Resource private SceneService scenes;
    @Resource private DecisionExecutionService decisions;

    @Test
    void resolvesTypedDecimalOnlyAtRuntimeAndPinsVersion() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer remote = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        remote.createContext("/margin", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"data\":{\"margin\":25.50}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        remote.start();
        try {
            String suffix = Long.toString(System.nanoTime());
            String code = "margin" + suffix;
            String flowCode = "variableFlow" + suffix;
            String sceneCode = "variableScene" + suffix;
            String url = "http://localhost:" + remote.getAddress().getPort() + "/margin";
            VariableVersion first = variables.create(code, "优惠前毛利", VariableValueType.DECIMAL,
                    "HTTP_GET", url, 1000, List.of("userId"), "data.margin");
            GraphDefinition graph = graph(code, first.getId());
            flows.create(flowCode, "变量查询决策");
            assertThat(flows.validate(flowCode,
                    graph(code, first.getId(), VariableValueType.STRING, "20", "EQ")).isValid()).isFalse();
            assertThat(flows.validate(flowCode, graph).isValid()).isTrue();
            flows.saveDraft(flowCode, graph, 0);
            flows.publish(flowCode, 1);
            assertThat(calls.get()).isZero();
            scenes.create(sceneCode, "变量场景", "只读变量");
            scenes.bindFlow(sceneCode, flowCode);
            ExecutionResult result = decisions.evaluate("variable-test", sceneCode, Map.of("userId", "user123"));
            assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(result.getBusinessResult().getConclusion()).isEqualTo("ELIGIBLE");
            assertThat((java.math.BigDecimal) result.getNodeResults().get("margin"))
                    .isEqualByComparingTo("25.50");
            assertThat(calls.get()).isEqualTo(1);
            variables.update(code, 1, "毛利新版", VariableValueType.DECIMAL,
                    "HTTP_GET", url, 1000, List.of("userId"), "data.margin");
            assertThat(decisions.evaluate("variable-test-two", sceneCode, Map.of("userId", "user123"))
                    .getBusinessResult().getConclusion()).isEqualTo("ELIGIBLE");
            assertThat(flows.validate(flowCode, graph).isValid()).isFalse();
        } finally { remote.stop(0); }
    }

    @Test
    void comparesStringAndBooleanVariablesWithTypedEquality() throws Exception {
        HttpServer remote = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        remote.createContext("/string", exchange -> respond(exchange, "{\"value\":\"GOLD\"}"));
        remote.createContext("/boolean", exchange -> respond(exchange, "{\"value\":true}"));
        remote.start();
        try {
            for (VariableValueType type : List.of(VariableValueType.STRING, VariableValueType.BOOLEAN)) {
                String suffix = type.name() + System.nanoTime();
                String code = "typed" + suffix;
                String flowCode = "typedFlow" + suffix;
                String sceneCode = "typedScene" + suffix;
                String path = type == VariableValueType.STRING ? "string" : "boolean";
                VariableVersion version = variables.create(code, "类型测试", type, "HTTP_GET",
                        "http://localhost:" + remote.getAddress().getPort() + "/" + path,
                        1000, List.of("userId"), "value");
                Object expected = type == VariableValueType.STRING ? "GOLD" : true;
                GraphDefinition graph = graph(code, version.getId(), type, expected, "EQ");
                flows.create(flowCode, "变量类型流程");
                assertThat(flows.validate(flowCode, graph).isValid()).isTrue();
                flows.saveDraft(flowCode, graph, 0);
                flows.publish(flowCode, 1);
                scenes.create(sceneCode, "变量类型场景", "验证类型");
                scenes.bindFlow(sceneCode, flowCode);
                assertThat(decisions.evaluate("typed-" + suffix, sceneCode, Map.of("userId", "u1"))
                        .getBusinessResult().getConclusion()).isEqualTo("ELIGIBLE");
            }
        } finally { remote.stop(0); }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws java.io.IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static GraphDefinition graph(String variableCode, long versionId) {
        return graph(variableCode, versionId, VariableValueType.DECIMAL, 20, "GTE");
    }

    private static GraphDefinition graph(String variableCode, long versionId, VariableValueType type,
                                         Object expected, String operator) {
        GraphDefinition result = new GraphDefinition(1, List.of(
                node("start", NodeKind.START, null, Map.of()),
                node("margin", NodeKind.COMPONENT, "get-variable", Map.of("variableCode", variableCode,
                        "versionId", versionId, "inputMappings", Map.of("userId", "userId"))),
                node("check", NodeKind.CONDITION, "compare-value", Map.of("sourceNodeId", "margin",
                        "operator", operator, "valueType", type.name(), "expected", expected)),
                node("yes", NodeKind.RESULT, null, Map.of("conclusion", "ELIGIBLE", "reasonCode", "MARGIN", "reason", "满足")),
                node("no", NodeKind.RESULT, null, Map.of("conclusion", "INELIGIBLE", "reasonCode", "MARGIN", "reason", "不足")),
                node("join", NodeKind.CONDITION_JOIN, null, Map.of()),
                node("end", NodeKind.END, null, Map.of())), List.of(
                edge("a", "start", "margin", null), edge("b", "margin", "check", null),
                edge("c", "check", "yes", "true"), edge("d", "check", "no", "false"),
                edge("e", "yes", "join", null), edge("f", "no", "join", null),
                edge("g", "join", "end", null)), Map.of());
        result.setRequiredInputs(List.of("userId"));
        return result;
    }

    private static GraphNode node(String id, NodeKind kind, String component, Map<String, Object> config) {
        return new GraphNode(id, kind, component,
                kind == NodeKind.COMPONENT || kind == NodeKind.CONDITION ? FailurePolicy.FAIL_FAST : null, config);
    }

    private static GraphEdge edge(String id, String source, String target, String branch) {
        return new GraphEdge(id, source, target, branch);
    }
}
