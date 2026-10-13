package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 场景流程和决策 HTTP 接口验证。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlowApiTest {
    @Value("${local.server.port}") int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void validatesPublishesAndEvaluatesAScene() throws Exception {
        GraphDefinition graph = FlowPublicationServiceTest.simpleGraph(27);
        HttpResponse<String> validation = send("POST", "/api/scenes/apiScene/validate", json.writeValueAsString(graph));
        assertThat(validation.statusCode()).isEqualTo(200);
        assertThat(validation.body()).contains("\"code\":\"SUCCESS\"")
                .contains("\"data\":{").contains("\"valid\":true");

        HttpResponse<String> draft = send("PUT", "/api/scenes/apiScene/draft?expectedRevision=0", json.writeValueAsString(graph));
        assertThat(draft.statusCode()).isEqualTo(200);
        HttpResponse<String> loaded = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/scenes/apiScene/draft"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(loaded.statusCode()).isEqualTo(200);
        assertThat(loaded.body()).contains("\"code\":\"SUCCESS\"")
                .contains("\"revision\":1").contains("\"id\":\"emit\"");
        HttpResponse<String> publish = send("POST", "/api/scenes/apiScene/publish", "{\"expectedRevision\":1}");
        assertThat(publish.statusCode()).isEqualTo(200);
        assertThat(publish.body()).contains("\"code\":\"SUCCESS\"")
                .contains("\"chainId\"").doesNotContain("compiledEl", "graph");
        HttpResponse<String> evaluation = send("POST", "/api/decisions/evaluate",
                "{\"requestId\":\"api-request\",\"sceneCode\":\"apiScene\",\"input\":{}}");
        assertThat(evaluation.statusCode()).isEqualTo(200);
        assertThat(evaluation.body()).contains("\"code\":\"SUCCESS\"")
                .contains("\"status\":\"SUCCEEDED\"")
                .contains("\"emit\":27");
    }

    @Test
    void validationLocatesInvalidComponentConfiguration() throws Exception {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("bad", NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST, Map.of()),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "bad", null),
                        new GraphEdge("b", "bad", "end", null)), Map.of());

        HttpResponse<String> response = send("POST", "/api/scenes/invalidApi/validate", json.writeValueAsString(graph));

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("\"code\":\"GRAPH_INVALID\"")
                .contains("\"data\":{").contains("COMPONENT_CONFIG_INVALID")
                .contains("\"nodeId\":\"bad\"");
    }

    @Test
    void rejectsAnOversizedRequestBeforeGraphDeserialization() throws Exception {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                new GraphNode("start", NodeKind.START, null, null, Map.of()),
                new GraphNode("large", NodeKind.COMPONENT, "emit-value", FailurePolicy.FAIL_FAST,
                        Map.of("value", "x".repeat(1_100_000))),
                new GraphNode("end", NodeKind.END, null, null, Map.of())),
                List.of(new GraphEdge("a", "start", "large", null),
                        new GraphEdge("b", "large", "end", null)), Map.of());

        HttpResponse<String> response = send("PUT", "/api/scenes/tooLarge/draft?expectedRevision=0", json.writeValueAsString(graph));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"")
                .contains("\"data\":null");
    }

    @Test
    void returnsClientErrorForMalformedJson() throws Exception {
        HttpResponse<String> response = send("POST", "/api/scenes/badJson/validate", "{invalid");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("INVALID_REQUEST");
    }

    @Test
    void returnsGraphIssuesThroughTheGlobalErrorEnvelope() throws Exception {
        GraphDefinition graph = new GraphDefinition(1, List.of(
                new GraphNode("duplicate", NodeKind.START, null, null, Map.of()),
                new GraphNode("duplicate", NodeKind.END, null, null, Map.of())), List.of(), Map.of());

        HttpResponse<String> response = send("PUT", "/api/scenes/invalidDraft/draft?expectedRevision=0",
                json.writeValueAsString(graph));

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("\"code\":\"GRAPH_INVALID\"")
                .contains("\"data\":[").contains("DUPLICATE_NODE_ID");
    }

    @Test
    void returnsSharedEnvelopeForMissingDraft() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/scenes/noDraft/draft"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":\"NOT_FOUND\"")
                .contains("\"data\":null");
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
