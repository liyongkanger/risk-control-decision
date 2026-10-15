package com.dmp.riskdecision.component.variable;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证变量定义、不可变版本、后台分页和强类型试取值。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VariableCenterApiTest {
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void createsVersionsAndResolvesTypedValue() throws Exception {
        String catalog = data(send("GET", "/api/components", null)).toString();
        assertThat(catalog).contains("get-variable", "compare-value").doesNotContain("http-json-get");
        assertThat(send("GET", "/api/rpc-operations", null).statusCode()).isEqualTo(404);
        AtomicInteger calls = new AtomicInteger();
        HttpServer remote = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        remote.createContext("/lookup", exchange -> {
            calls.incrementAndGet();
            assertThat(exchange.getRequestURI().getRawQuery()).isEqualTo("userId=user123");
            byte[] body = "{\"data\":{\"amount\":12.50}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        remote.start();
        try {
            String code = "amount" + System.nanoTime();
            String url = "http://localhost:" + remote.getAddress().getPort() + "/lookup";
            String body = "{\"variableCode\":\"" + code + "\",\"variableName\":\"权益金额\","
                    + "\"valueType\":\"DECIMAL\",\"sourceType\":\"HTTP_GET\",\"url\":\"" + url
                    + "\",\"timeoutMs\":1000,\"parameterNames\":[\"userId\"],"
                    + "\"resultPath\":\"data.amount\"}";
            JsonNode first = data(send("POST", "/api/variables", body));
            assertThat(first.get("versionNumber").asInt()).isEqualTo(1);
            assertThat(first.get("valueType").asText()).isEqualTo("DECIMAL");
            assertThat(data(send("GET", "/api/variables?keyword=" + code + "&page=1&pageSize=10", null))
                    .get("total").asInt()).isEqualTo(1);
            JsonNode tested = data(send("POST", "/api/variables/" + code + "/test",
                    "{\"input\":{\"userId\":\"user123\"}}"));
            assertThat(tested.get("value").decimalValue()).isEqualByComparingTo("12.50");
            assertThat(calls.get()).isEqualTo(1);
            JsonNode second = data(send("PUT", "/api/variables/" + code,
                    body.replace("\"variableCode\":\"" + code + "\",", "\"expectedVersion\":1,")
                            .replace("data.amount", "data.missing")));
            assertThat(second.get("versionNumber").asInt()).isEqualTo(2);
            assertThat(second.get("id").asLong()).isNotEqualTo(first.get("id").asLong());
            HttpResponse<String> failedSource = send("POST", "/api/variables/" + code + "/test",
                    "{\"input\":{\"userId\":\"user123\"}}");
            assertThat(failedSource.statusCode()).isEqualTo(502);
            assertThat(json.readTree(failedSource.body()).get("code").asText())
                    .isEqualTo("VARIABLE_RESOLUTION_FAILED");
            assertThat(send("POST", "/api/variables", body.replace("localhost", "example.com"))
                    .statusCode()).isEqualTo(422);
        } finally { remote.stop(0); }
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
