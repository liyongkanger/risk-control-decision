package com.dmp.riskdecision.business.marketing;

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

/** 验证演示接口从真实 HTTP 到决策、反馈和报告的调用链。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketingDemoApiTest {
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void runsFixtureAndAppliesManualFeedbackThroughHttp() throws Exception {
        String experiment = "apiDemo" + System.nanoTime();
        JsonNode setup = data(request("POST", "/setup", ""));
        assertThat(setup.get("sceneCode").asText()).isEqualTo(MarketingDemoFlows.SCENE_CODE);
        JsonNode report = data(request("POST", "/run-fixture",
                "{\"experimentId\":\"" + experiment + "\"}"));
        assertThat(report.get("attempts").size()).isEqualTo(30);
        assertThat(report.get("groups").get("LOW").get("averageContributionProfit").decimalValue())
                .isEqualByComparingTo("7.50");
        assertThat(data(request("GET", "/report?experimentId=" + experiment, null))
                .get("groups").get("HIGH").get("paidUsers").asInt()).isEqualTo(4);

        String checkout = "{\"experimentId\":\"" + experiment + "\",\"checkoutId\":\"manual\","
                + "\"userId\":\"manualUser\",\"orderAmount\":120,"
                + "\"preDiscountMargin\":30,\"existingOfferAmount\":3}";
        JsonNode attempt = data(request("POST", "/checkout", checkout));
        assertThat(attempt.get("decisionStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(attempt.get("subflowVersionIds").get("selectionCall").asLong()).isPositive();
        String settled = "{\"experimentId\":\"" + experiment + "\",\"checkoutId\":\"manual\","
                + "\"eventId\":\"paidOne\",\"eventType\":\"SETTLED\","
                + "\"paid\":true,\"appliedOfferCode\":\"EXISTING\"}";
        assertThat(data(request("POST", "/feedback", settled)).get("contributionProfit").decimalValue())
                .isEqualByComparingTo("27.00");
        assertThat(data(request("POST", "/feedback", settled)).get("contributionProfit").decimalValue())
                .isEqualByComparingTo("27.00");
        assertThat(request("POST", "/feedback", settled.replace("EXISTING", "NONE"))
                .statusCode()).isEqualTo(409);
        JsonNode refunded = data(request("POST", "/feedback",
                "{\"experimentId\":\"" + experiment + "\",\"checkoutId\":\"manual\","
                + "\"eventId\":\"refundOne\",\"eventType\":\"REFUNDED\"}"));
        assertThat(refunded.get("contributionProfit").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(request("POST", "/checkout", checkout.replace("120", "-1"))
                .statusCode()).isEqualTo(422);
        assertThat(request("POST", "/checkout", checkout.replace("120", "121"))
                .statusCode()).isEqualTo(409);
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/demo/checkout-offer" + path))
                .header("Content-Type", "application/json");
        if ("POST".equals(method)) builder.POST(HttpRequest.BodyPublishers.ofString(body));
        else builder.GET();
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode root = json.readTree(response.body());
        assertThat(root.get("code").asText()).isEqualTo("SUCCESS");
        return root.get("data");
    }
}
