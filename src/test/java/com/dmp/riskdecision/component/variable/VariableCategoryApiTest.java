package com.dmp.riskdecision.component.variable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证变量分类属于逻辑变量，分页筛选与历史版本始终读取当前归属。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VariableCategoryApiTest {
    @Value("${local.server.port}") private int port;
    @Autowired private JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void categoriesAndCreationDefaults() throws Exception {
        JsonNode categories = data(send("GET", "/api/variables/categories", null));
        assertThat(categories.size()).isEqualTo(6);
        assertThat(categories.toString()).contains("USER", "TRANSACTION", "CAMPAIGN", "BUDGET", "RISK", "OTHER");

        String code = "categoryDefault" + System.nanoTime();
        JsonNode created = data(send("POST", "/api/variables", body(code, null)));
        assertThat(created.get("categoryCode").asText()).isEqualTo("OTHER");
        assertThat(data(send("GET", "/api/variables/" + code, null)).get("categoryCode").asText())
                .isEqualTo("OTHER");
        assertThat(send("POST", "/api/variables", body("badCategory" + System.nanoTime(), "UNKNOWN"))
                .statusCode()).isEqualTo(422);
    }

    @Test
    void filtersAndCategoryEditPreserveVersions() throws Exception {
        String code = "categoryFilter" + System.nanoTime();
        JsonNode first = data(send("POST", "/api/variables", body(code, "USER")));
        long firstId = first.get("id").asLong();
        JsonNode second = data(send("PUT", "/api/variables/" + code,
                "{\"expectedVersion\":1,\"variableName\":\"分类测试\",\"valueType\":\"STRING\","
                        + "\"sourceType\":\"REQUEST_INPUT\"}"));
        long secondId = second.get("id").asLong();
        assertThat(second.get("categoryCode").asText()).isEqualTo("USER");
        assertThat(data(send("GET", "/api/variables?keyword=" + code
                + "&categoryCode=USER&sourceType=REQUEST_INPUT&valueType=STRING&page=1&pageSize=10", null))
                .get("total").asLong()).isEqualTo(1);
        assertThat(data(send("GET", "/api/variables?keyword=" + code
                + "&categoryCode=RISK&page=1&pageSize=10", null)).get("total").asLong()).isZero();

        JsonNode edited = data(send("PUT", "/api/variables/" + code + "/category",
                "{\"categoryCode\":\"RISK\"}"));
        assertThat(edited.get("id").asLong()).isEqualTo(secondId);
        assertThat(edited.get("versionNumber").asLong()).isEqualTo(2);
        assertThat(edited.get("categoryCode").asText()).isEqualTo("RISK");
        assertThat(data(send("GET", "/api/variables/versions/" + firstId, null))
                .get("categoryCode").asText()).isEqualTo("RISK");
        assertThat(data(send("GET", "/api/variables?keyword=" + code
                + "&categoryCode=RISK&sourceType=REQUEST_INPUT&valueType=STRING&page=1&pageSize=10", null))
                .get("total").asLong()).isEqualTo(1);
        assertThat(data(send("GET", "/api/variables?keyword=" + code
                + "&categoryCode=USER&page=1&pageSize=10", null)).get("total").asLong()).isZero();
        assertThat(send("PUT", "/api/variables/" + code + "/category",
                "{\"categoryCode\":\"UNKNOWN\"}").statusCode()).isEqualTo(422);
        assertThat(send("PUT", "/api/variables/missingVariable/category",
                "{\"categoryCode\":\"USER\"}").statusCode()).isEqualTo(404);
    }

    @Test
    void rollsBackNewVersionWhenCategoryInsertFails() throws Exception {
        String code = "categoryConflict" + System.nanoTime();
        jdbc.update("INSERT INTO tb_variable_category (variable_code, category_code) VALUES (?, ?)",
                code, "OTHER");
        assertThat(send("POST", "/api/variables", body(code, "USER")).statusCode()).isEqualTo(500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_variable_version WHERE variable_code = ?",
                Long.class, code)).isZero();
    }

    private String body(String code, String category) {
        return "{\"variableCode\":\"" + code + "\",\"variableName\":\"分类测试\","
                + "\"valueType\":\"STRING\",\"sourceType\":\"REQUEST_INPUT\""
                + (category == null ? "" : ",\"categoryCode\":\"" + category + "\"") + "}";
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
        return json.readTree(response.body()).get("data");
    }
}
