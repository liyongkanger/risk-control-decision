package com.dmp.riskdecision.rule;

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

/** 从后台 API 验证规则集草稿、试算、发布和不可变旧版本。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RuleSetApiTest {
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void keepsPublishedVersionWhenDraftChanges() throws Exception {
        String code = "rule" + System.nanoTime();
        JsonNode created = data(send("POST", "/api/rule-sets", "{\"code\":\"" + code
                + "\",\"name\":\"资格\",\"definition\":" + definition(100) + "}"));
        assertThat(created.get("draftRevision").asLong()).isEqualTo(1);
        JsonNode preview = data(send("POST", "/api/rule-sets/" + code + "/test",
                "{\"input\":{\"amount\":80}}"));
        assertThat(preview.get("resultCode").asText()).isEqualTo("INELIGIBLE");
        assertThat(preview.get("matchedRuleId").asText()).isEqualTo("min-order");
        JsonNode first = data(send("POST", "/api/rule-sets/" + code + "/publish",
                "{\"expectedRevision\":1}"));
        assertThat(first.get("versionNumber").asLong()).isEqualTo(1);
        JsonNode updated = data(send("PUT", "/api/rule-sets/" + code + "/draft",
                "{\"expectedRevision\":1,\"definition\":" + definition(50) + "}"));
        assertThat(updated.get("draftRevision").asLong()).isEqualTo(2);
        assertThat(send("POST", "/api/rule-sets/" + code + "/publish",
                "{\"expectedRevision\":1}").statusCode()).isEqualTo(409);
        JsonNode second = data(send("POST", "/api/rule-sets/" + code + "/publish",
                "{\"expectedRevision\":2}"));
        assertThat(second.get("versionNumber").asLong()).isEqualTo(2);
        JsonNode old = data(send("GET", "/api/rule-sets/versions/" + first.get("id").asLong(), null));
        assertThat(old.get("definition").get("rows").get(0).get("conditions").get(0)
                .get("expected").asInt()).isEqualTo(100);
        JsonNode oldTest = data(send("POST", "/api/rule-sets/" + code + "/versions/"
                + first.get("id").asLong() + "/test", "{\"input\":{\"amount\":80}}"));
        assertThat(oldTest.get("resultCode").asText()).isEqualTo("INELIGIBLE");
        assertThat(oldTest.get("ruleVersionId").asLong()).isEqualTo(first.get("id").asLong());
        JsonNode newTest = data(send("POST", "/api/rule-sets/" + code + "/versions/"
                + second.get("id").asLong() + "/test", "{\"input\":{\"amount\":80}}"));
        assertThat(newTest.get("resultCode").asText()).isEqualTo("ELIGIBLE");
        JsonNode page = data(send("GET", "/api/rule-sets?keyword=" + code + "&page=1&pageSize=10", null));
        assertThat(page.get("total").asLong()).isEqualTo(1);
        assertThat(page.get("records").get(0).has("definition")).isFalse();
    }

    @Test
    void rejectsInvalidRuleDefinitionsAndMissingResources() throws Exception {
        String code = "bad" + System.nanoTime();
        String valid = definition(100);
        String wrongOperator = valid.replace("\"operator\":\"LT\"", "\"operator\":\"CONTAINS\"");
        assertThat(send("POST", "/api/rule-sets", "{\"code\":\"" + code
                + "\",\"name\":\"坏规则\",\"definition\":" + wrongOperator + "}").statusCode())
                .isEqualTo(422);
        String wrongResult = valid.replace("\"resultCode\":\"INELIGIBLE\"", "\"resultCode\":\"UNKNOWN\"");
        assertThat(send("POST", "/api/rule-sets", "{\"code\":\"" + code
                + "\",\"name\":\"坏规则\",\"definition\":" + wrongResult + "}").statusCode())
                .isEqualTo(422);
        String missingOperator = valid.replace("\"operator\":\"LT\"", "\"operator\":null");
        assertThat(send("POST", "/api/rule-sets", "{\"code\":\"" + code
                + "\",\"name\":\"坏规则\",\"definition\":" + missingOperator + "}").statusCode())
                .isEqualTo(422);
        String missingType = valid.replace("\"type\":\"DECIMAL\"", "\"type\":null");
        assertThat(send("POST", "/api/rule-sets", "{\"code\":\"" + code
                + "\",\"name\":\"坏规则\",\"definition\":" + missingType + "}").statusCode())
                .isEqualTo(422);
        assertThat(send("GET", "/api/rule-sets/" + code, null).statusCode()).isEqualTo(404);
    }

    @Test
    void comparesJsonDecimalLiteralsWithoutBinaryRounding() throws Exception {
        String code = "precision" + System.nanoTime();
        String definition = definition(100).replace("\"expected\":100", "\"expected\":0.10000000000000001");
        JsonNode created = data(send("POST", "/api/rule-sets", "{\"code\":\"" + code
                + "\",\"name\":\"精度\",\"definition\":" + definition + "}"));
        assertThat(created.get("definition").get("rows").get(0).get("conditions").get(0)
                .get("expected").isString()).isTrue();
        JsonNode result = data(send("POST", "/api/rule-sets/" + code + "/test",
                "{\"input\":{\"amount\":0.1}}"));
        assertThat(result.get("resultCode").asText()).isEqualTo("INELIGIBLE");
        String quoted = definition.replace("0.10000000000000001", "\"0.10000000000000001\"");
        String otherCode = "quoted" + System.nanoTime();
        data(send("POST", "/api/rule-sets", "{\"code\":\"" + otherCode
                + "\",\"name\":\"精度文本\",\"definition\":" + quoted + "}"));
        JsonNode quotedResult = data(send("POST", "/api/rule-sets/" + otherCode + "/test",
                "{\"input\":{\"amount\":\"0.1\"}}"));
        assertThat(quotedResult.get("resultCode").asText()).isEqualTo("INELIGIBLE");
    }

    private String definition(int threshold) {
        return "{\"inputs\":[{\"name\":\"amount\",\"type\":\"DECIMAL\"}],"
                + "\"resultCodes\":[\"ELIGIBLE\",\"INELIGIBLE\"],"
                + "\"rows\":[{\"id\":\"min-order\",\"priority\":10,\"conditions\":[{\"input\":\"amount\","
                + "\"operator\":\"LT\",\"expected\":" + threshold + "}],\"result\":{\"resultCode\":\"INELIGIBLE\","
                + "\"reasonCode\":\"ORDER_LOW\",\"reason\":\"金额不足\"}}],"
                + "\"defaultResult\":{\"resultCode\":\"ELIGIBLE\",\"reasonCode\":\"QUALIFIED\",\"reason\":\"符合资格\"}}";
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json");
        if ("GET".equals(method)) request.GET();
        else request.method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode root = json.readTree(response.body());
        assertThat(root.get("code").asText()).isEqualTo("SUCCESS");
        return root.get("data");
    }
}
