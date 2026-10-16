package com.dmp.riskdecision.decision;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证营销调用与通用执行记录使用同一个执行标识。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DecisionRecordApiTest {
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void marketingCheckoutLinksToItsPersistedDecisionTrace() throws Exception {
        assertThat(post("/api/demo/checkout-offer/setup", null).statusCode()).isEqualTo(200);
        String experiment = "recordApi" + System.nanoTime();
        JsonNode attempt = data(post("/api/demo/checkout-offer/checkout", """
                {"experimentId":"%s","checkoutId":"order001","userId":"user001",
                 "orderAmount":120,"preDiscountMargin":30,"existingOfferAmount":0}
                """.formatted(experiment)));
        String id = attempt.get("executionId").asText();
        assertThat(id).matches("[0-9a-f-]{36}");

        JsonNode detail = data(get("/api/decision-records/" + id));
        assertThat(detail.get("executionId").asText()).isEqualTo(id);
        assertThat(detail.get("sceneCode").asText()).isEqualTo("demoCheckoutScene");
        assertThat(detail.get("result").get("trace").isArray()).isTrue();
        assertThat(detail.get("input").get("uid").asText()).isEqualTo("user001");

        JsonNode page = data(get("/api/decision-records?sceneCode=demoCheckoutScene&requestId="
                + experiment + "%3Aorder001&page=1&pageSize=10"));
        assertThat(page.get("total").asLong()).isEqualTo(1);
        assertThat(page.get("records").get(0).get("executionId").asText()).isEqualTo(id);
        assertThat(page.get("records").get(0).get("result").isNull()).isTrue();
    }

    @Test
    void failedDecisionAlsoGetsAQueryableExecutionId() throws Exception {
        assertThat(post("/api/demo/checkout-offer/setup", null).statusCode()).isEqualTo(200);
        String requestId = "recordFail" + System.nanoTime();
        JsonNode failed = data(post("/api/decisions/evaluate", """
                {"requestId":"%s","sceneCode":"demoCheckoutScene",
                 "input":{"uid":"missing","checkoutId":"missing","experimentId":"missing",
                          "inExperiment":true,"highGroup":false}}
                """.formatted(requestId)));
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");
        String id = failed.get("executionId").asText();
        assertThat(data(get("/api/decision-records/" + id)).get("status").asText())
                .isEqualTo("FAILED");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        return client.send(request.POST(body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode root = json.readTree(response.body());
        assertThat(root.get("code").asText()).isEqualTo("SUCCESS");
        return root.get("data");
    }
}
