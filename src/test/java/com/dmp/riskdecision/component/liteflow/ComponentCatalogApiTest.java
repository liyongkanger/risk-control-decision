package com.dmp.riskdecision.component.liteflow;

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

/** 只读组件目录的字段契约与后台分页查询验收。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ComponentCatalogApiTest {
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void exposesDescriptionsAndPagesByNodeKind() throws Exception {
        JsonNode all = data("/api/components");
        assertThat(all.size()).isEqualTo(7);
        JsonNode variable = null;
        JsonNode rule = null;
        for (JsonNode item : all) {
            if ("get-variable".equals(item.get("key").asText())) variable = item;
            if ("execute-rule-set".equals(item.get("key").asText())) rule = item;
        }
        assertThat(variable).isNotNull();
        assertThat(variable.get("description").asText()).contains("变量中心");
        assertThat(variable.get("outputType").asText()).isEqualTo("VARIABLE_VALUE");
        assertThat(rule).isNotNull();
        assertThat(rule.get("outputType").asText()).isEqualTo("RULE_RESULT");

        JsonNode first = data("/api/components/page?keyword=&nodeKind=CONDITION&page=1&pageSize=2");
        assertThat(first.get("records").size()).isEqualTo(2);
        assertThat(first.get("total").asInt()).isEqualTo(4);
        assertThat(first.get("page").asInt()).isEqualTo(1);
        assertThat(first.get("pageSize").asInt()).isEqualTo(2);
        JsonNode second = data("/api/components/page?keyword=&nodeKind=CONDITION&page=2&pageSize=2");
        assertThat(second.get("records").size()).isEqualTo(2);
        JsonNode rules = data("/api/components/page?category=RULE&page=1&pageSize=10");
        assertThat(rules.get("total").asInt()).isEqualTo(2);
        assertThat(rules.get("records").get(0).get("category").asText()).isEqualTo("RULE");
        assertThat(request("/api/components/page?page=0&pageSize=10").statusCode()).isEqualTo(422);
    }

    private HttpResponse<String> request(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(String path) throws Exception {
        HttpResponse<String> response = request(path);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode root = json.readTree(response.body());
        assertThat(root.get("code").asText()).isEqualTo("SUCCESS");
        return root.get("data");
    }
}
