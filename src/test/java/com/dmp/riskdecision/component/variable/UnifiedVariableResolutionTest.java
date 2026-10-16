package com.dmp.riskdecision.component.variable;

import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableResolutionException;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.orchestrator.graph.FlowContextBinding;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证变量版本在一次流程中的请求取值和固定依赖。 */
@SpringBootTest
class UnifiedVariableResolutionTest {
    @Resource private VariableService variables;

    @Test
    void requestInputUsesVariableCodeAndRejectsMissingOrWrongType() {
        String code = "request" + System.nanoTime();
        VariableVersion version = variables.create(code, "请求金额", VariableValueType.DECIMAL,
                "REQUEST_INPUT", null, 0, List.of(), null);

        assertThat(variables.resolveInFlow(version.getId(), code,
                Map.of(code, new BigDecimal("12.50")), Map.of(), Map.of()))
                .isEqualTo(new BigDecimal("12.50"));
        assertThatThrownBy(() -> variables.resolveInFlow(version.getId(), code,
                Map.of(), Map.of(), Map.of())).isInstanceOf(VariableResolutionException.class);
        assertThatThrownBy(() -> variables.resolveInFlow(version.getId(), code,
                Map.of(code, "not-a-decimal"), Map.of(), Map.of())).isInstanceOf(VariableResolutionException.class);
    }

    @Test
    void externalDependenciesStayPinnedAfterRequestVersionChanges() throws Exception {
        HttpServer remote = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        remote.createContext("/value", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            byte[] body = ("{\"value\":\"" + query + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        remote.start();
        try {
            String dependencyCode = "locator" + System.nanoTime();
            VariableVersion first = variables.create(dependencyCode, "定位字段", VariableValueType.STRING,
                    "REQUEST_INPUT", null, 0, List.of(), null);
            String externalCode = "external" + System.nanoTime();
            VariableVersion external = variables.create(externalCode, "外部变量", VariableValueType.STRING,
                    "HTTP_GET", "http://localhost:" + remote.getAddress().getPort() + "/value",
                    1000, List.of(dependencyCode), "value",
                    Map.of(dependencyCode, first.getId()));
            variables.update(dependencyCode, 1, "定位字段新版", VariableValueType.DECIMAL,
                    "REQUEST_INPUT", null, 0, List.of(), null);

            assertThat(external.getDependencyVersionIds()).containsEntry(dependencyCode, first.getId());
            assertThat(variables.requireVersion(external.getId()).getDependencyVersionIds())
                    .containsEntry(dependencyCode, first.getId());
            assertThat(variables.resolveInFlow(external.getId(), externalCode,
                    Map.of(dependencyCode, "user-1"), Map.of(), Map.of()))
                    .isEqualTo(dependencyCode + "=user-1");
            assertThatThrownBy(() -> variables.resolveInFlow(external.getId(), externalCode,
                    Map.of(dependencyCode, new BigDecimal("1")), Map.of(), Map.of()))
                    .isInstanceOf(VariableResolutionException.class);
        } finally { remote.stop(0); }
    }

    @Test
    void rejectsDependencyCycleAtDefinitionTime() {
        String code = "cycle" + System.nanoTime();
        assertThatThrownBy(() -> variables.create(code, "循环", VariableValueType.STRING,
                "HTTP_GET", "http://localhost:1/value", 1000, List.of(code), "value",
                Map.of(code, 1L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void externalVariableRejectsUnpinnedDependency() {
        String code = "unpinned" + System.nanoTime();
        assertThatThrownBy(() -> variables.create(code, "未固定依赖", VariableValueType.STRING,
                "HTTP_GET", "http://localhost:1/value", 1000, List.of("userId"), "value"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flowContextReadsOnlyBoundNodeAndNestedPath() {
        String code = "context" + System.nanoTime();
        VariableVersion version = variables.create(code, "节点金额", VariableValueType.DECIMAL,
                "FLOW_CONTEXT", null, 0, List.of(), null);
        FlowContextBinding binding = new FlowContextBinding(code, version.getId(), "producer", "data.amount");
        assertThat(variables.resolveInFlow(version.getId(), code, Map.of(code, 999),
                Map.of("producer", Map.of("data", Map.of("amount", 8))), Map.of(code, binding)))
                .isEqualTo(new BigDecimal("8"));
        assertThatThrownBy(() -> variables.resolveInFlow(version.getId(), code, Map.of(),
                Map.of("other", Map.of("data", Map.of("amount", 8))), Map.of(code, binding)))
                .isInstanceOf(VariableResolutionException.class);
    }

    @Test
    void flowContextCannotFallBackToAnUnboundRequestField() {
        String code = "isolated" + System.nanoTime();
        VariableVersion version = variables.create(code, "隔离上下文", VariableValueType.DECIMAL,
                "FLOW_CONTEXT", null, 0, List.of(), null);

        assertThatThrownBy(() -> variables.resolveInFlow(version.getId(), code,
                Map.of(code, 999), Map.of(), Map.of()))
                .isInstanceOf(VariableResolutionException.class)
                .hasMessageContaining(code);
    }

    @Test
    void flowContextTraversesObjectGettersAndAllowsWholeScalarResult() {
        String code = "object" + System.nanoTime();
        VariableVersion version = variables.create(code, "对象结果", VariableValueType.DECIMAL,
                "FLOW_CONTEXT", null, 0, List.of(), null);
        FlowContextBinding nested = new FlowContextBinding(code, version.getId(), "producer", "data.amount");
        assertThat(variables.resolveInFlow(version.getId(), code, Map.of(),
                Map.of("producer", new SampleNodeResult(Map.of("amount", 9))), Map.of(code, nested)))
                .isEqualTo(new BigDecimal("9"));
        FlowContextBinding scalar = new FlowContextBinding(code, version.getId(), "scalar", "");
        assertThat(variables.resolveInFlow(version.getId(), code, Map.of(),
                Map.of("scalar", 7), Map.of(code, scalar))).isEqualTo(new BigDecimal("7"));
    }

    public static class SampleNodeResult {
        private final Map<String, Object> data;

        private SampleNodeResult(Map<String, Object> data) { this.data = data; }

        public Map<String, Object> getData() { return data; }
    }
}
