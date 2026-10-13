package com.dmp.riskdecision.policy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证场景基本信息的 HTTP 管理接口。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SceneApiTest {
    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void createsSearchesAndUpdatesSceneMetadata() throws Exception {
        String code = "scene" + System.nanoTime();
        HttpResponse<String> created = request("POST", "/api/scenes", """
                {"sceneCode":"%s","sceneName":"支付风控","description":"支付风险决策"}
                """.formatted(code));
        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.body()).contains("\"code\":\"SUCCESS\"")
                .contains("\"sceneCode\":\"" + code + "\"")
                .contains("\"sceneName\":\"支付风控\"");

        HttpResponse<String> detail = request("GET", "/api/scenes/" + code, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("\"publishedVersionNumber\":null");

        HttpResponse<String> listed = request("GET", "/api/scenes?keyword=" + code + "&page=1&pageSize=20", null);
        assertThat(listed.statusCode()).isEqualTo(200);
        assertThat(listed.body()).contains("\"total\":1")
                .contains("\"sceneCode\":\"" + code + "\"");

        HttpResponse<String> updated = request("PUT", "/api/scenes/" + code, """
                {"sceneName":"支付决策","description":"已更新"}
                """);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("\"sceneName\":\"支付决策\"")
                .contains("\"description\":\"已更新\"");
    }

    @Test
    void rejectsDuplicateAndInvalidSceneCodes() throws Exception {
        String code = "duplicate" + System.nanoTime();
        String body = """
                {"sceneCode":"%s","sceneName":"测试场景"}
                """.formatted(code);
        assertThat(request("POST", "/api/scenes", body).statusCode()).isEqualTo(200);

        HttpResponse<String> duplicate = request("POST", "/api/scenes", body);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("\"code\":\"SCENE_CODE_CONFLICT\"");

        HttpResponse<String> invalid = request("POST", "/api/scenes", """
                {"sceneCode":"bad code","sceneName":"测试场景"}
                """);
        assertThat(invalid.statusCode()).isEqualTo(422);
        assertThat(invalid.body()).contains("\"code\":\"INVALID_REQUEST\"");

        HttpResponse<String> missing = request("GET", "/api/scenes/noSuchScene", null);
        assertThat(missing.statusCode()).isEqualTo(404);
    }

    @Test
    void rejectsFlowOperationsForScenesThatWereNeverCreated() throws Exception {
        String code = "unknown" + System.nanoTime();
        String graph = json.writeValueAsString(FlowPublicationServiceTest.simpleGraph(1));

        HttpResponse<String> draft = request("PUT", "/api/scenes/" + code + "/draft?expectedRevision=0", graph);
        assertThat(draft.statusCode()).isEqualTo(404);
        assertThat(draft.body()).contains("\"code\":\"NOT_FOUND\"");

        HttpResponse<String> validation = request("POST", "/api/scenes/" + code + "/validate", graph);
        assertThat(validation.statusCode()).isEqualTo(404);

        HttpResponse<String> publication = request("POST", "/api/scenes/" + code + "/publish",
                "{\"expectedRevision\":1}");
        assertThat(publication.statusCode()).isEqualTo(404);
    }

    @Test
    void treatsUnderscoreInSearchKeywordAsLiteralText() throws Exception {
        String prefix = "literal" + System.nanoTime();
        String wanted = prefix + "_scene";
        String other = prefix + "Xscene";
        request("POST", "/api/scenes", "{\"sceneCode\":\"" + wanted + "\",\"sceneName\":\"目标场景\"}");
        request("POST", "/api/scenes", "{\"sceneCode\":\"" + other + "\",\"sceneName\":\"其他场景\"}");

        HttpResponse<String> response = request("GET", "/api/scenes?keyword=" + wanted + "&page=1&pageSize=20", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"total\":1")
                .contains("\"sceneCode\":\"" + wanted + "\"")
                .doesNotContain("\"sceneCode\":\"" + other + "\"");
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (body == null) {
            builder.GET();
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
