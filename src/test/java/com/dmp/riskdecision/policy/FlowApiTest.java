package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlowApiTest {
    @Value("${local.server.port}") int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void validatesPublishesAndEvaluatesAScene() throws Exception {
        GraphDefinition graph = FlowPublicationServiceTest.simpleGraph(27);
        var validation = send("POST", "/api/scenes/apiScene/validate", json.writeValueAsString(graph));
        assertThat(validation.statusCode()).isEqualTo(200);
        assertThat(validation.body()).contains("\"valid\":true");

        var draft = send("PUT", "/api/scenes/apiScene/draft?expectedRevision=0", json.writeValueAsString(graph));
        assertThat(draft.statusCode()).isEqualTo(200);
        var publish = send("POST", "/api/scenes/apiScene/publish", "{\"expectedRevision\":1}");
        assertThat(publish.statusCode()).isEqualTo(200);
        var evaluation = send("POST", "/api/decisions/evaluate",
                "{\"requestId\":\"api-request\",\"sceneCode\":\"apiScene\",\"input\":{}}");
        assertThat(evaluation.statusCode()).isEqualTo(200);
        assertThat(evaluation.body()).contains("\"status\":\"SUCCEEDED\"")
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

        var response = send("POST", "/api/scenes/invalidApi/validate", json.writeValueAsString(graph));

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("COMPONENT_CONFIG_INVALID").contains("\"nodeId\":\"bad\"");
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
