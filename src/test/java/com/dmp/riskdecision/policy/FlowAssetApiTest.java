package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.component.liteflow.service.LiteFlowChainService;
import com.dmp.riskdecision.policy.service.impl.FlowPublicationServiceImpl;
import jakarta.annotation.Resource;
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

/** 验证独立流程可发布并绑定场景，且场景发布版本不会随流程变化。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlowAssetApiTest {
    @Value("${local.server.port}")
    private int port;
    @Resource
    private FlowPublicationServiceImpl publication;
    @Resource
    private LiteFlowChainService chains;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void publishesFlowWithoutSceneAndBindsPinnedVersionToScene() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String flowCode = "shared" + suffix;
        String sceneCode = "consumer" + suffix;
        assertThat(request("POST", "/api/flows", "{\"flowCode\":\"" + flowCode
                + "\",\"flowName\":\"共享决策\"}").statusCode()).isEqualTo(200);

        String firstGraph = json.writeValueAsString(FlowPublicationServiceTest.simpleGraph(1));
        assertThat(request("PUT", "/api/flows/" + flowCode + "/draft?expectedRevision=0", firstGraph)
                .statusCode()).isEqualTo(200);
        JsonNode firstVersion = data(request("POST", "/api/flows/" + flowCode + "/publish",
                "{\"expectedRevision\":1}"));
        long firstId = firstVersion.get("id").asLong();

        assertThat(request("POST", "/api/scenes", "{\"sceneCode\":\"" + sceneCode
                + "\",\"sceneName\":\"测试场景\"}").statusCode()).isEqualTo(200);
        JsonNode bound = data(request("PUT", "/api/scenes/" + sceneCode + "/entry-flow",
                "{\"flowCode\":\"" + flowCode + "\"}"));
        assertThat(bound.get("flowCode").asText()).isEqualTo(flowCode);
        assertThat(bound.get("publishedVersionNumber").asLong()).isEqualTo(1);
        JsonNode legacyDraft = data(request("GET", "/api/scenes/" + sceneCode + "/draft", null));
        assertThat(legacyDraft.get("sceneCode").asText()).isEqualTo(sceneCode);
        assertThat(legacyDraft.get("revision").asLong()).isEqualTo(1);

        JsonNode firstDecision = data(request("POST", "/api/decisions/evaluate",
                "{\"sceneCode\":\"" + sceneCode + "\",\"input\":{}}"));
        assertThat(firstDecision.get("versionId").asLong()).isEqualTo(firstId);
        assertThat(firstDecision.get("nodeResults").get("emit").asInt()).isEqualTo(1);

        String secondGraph = json.writeValueAsString(FlowPublicationServiceTest.simpleGraph(2));
        assertThat(request("PUT", "/api/flows/" + flowCode + "/draft?expectedRevision=1", secondGraph)
                .statusCode()).isEqualTo(200);
        JsonNode secondVersion = data(request("POST", "/api/flows/" + flowCode + "/publish",
                "{\"expectedRevision\":2}"));
        assertThat(secondVersion.get("id").asLong()).isNotEqualTo(firstId);

        com.yomahub.liteflow.flow.FlowBus.removeChain(firstVersion.get("chainId").asText());
        com.yomahub.liteflow.flow.FlowBus.removeChain(secondVersion.get("chainId").asText());
        publication.loadPublishedAtStartup();
        assertThat(chains.contains(firstVersion.get("chainId").asText())).isTrue();
        assertThat(chains.contains(secondVersion.get("chainId").asText())).isTrue();

        JsonNode stillPinned = data(request("POST", "/api/decisions/evaluate",
                "{\"sceneCode\":\"" + sceneCode + "\",\"input\":{}}"));
        assertThat(stillPinned.get("versionId").asLong()).isEqualTo(firstId);
        assertThat(stillPinned.get("nodeResults").get("emit").asInt()).isEqualTo(1);
        data(request("PUT", "/api/scenes/" + sceneCode + "/entry-flow",
                "{\"flowCode\":\"" + flowCode + "\"}"));
        JsonNode updated = data(request("POST", "/api/decisions/evaluate",
                "{\"sceneCode\":\"" + sceneCode + "\",\"input\":{}}"));
        assertThat(updated.get("versionId").asLong()).isEqualTo(secondVersion.get("id").asLong());
        assertThat(updated.get("nodeResults").get("emit").asInt()).isEqualTo(2);

        String thirdGraph = json.writeValueAsString(FlowPublicationServiceTest.simpleGraph(3));
        assertThat(request("PUT", "/api/scenes/" + sceneCode + "/draft?expectedRevision=2", thirdGraph)
                .statusCode()).isEqualTo(200);
        JsonNode scenePublished = data(request("POST", "/api/scenes/" + sceneCode + "/publish",
                "{\"expectedRevision\":3}"));
        assertThat(scenePublished.get("sceneCode").asText()).isEqualTo(sceneCode);
        assertThat(scenePublished.get("versionNumber").asLong()).isEqualTo(3);
        JsonNode sceneDecision = data(request("POST", "/api/decisions/evaluate",
                "{\"sceneCode\":\"" + sceneCode + "\",\"input\":{}}"));
        assertThat(sceneDecision.get("nodeResults").get("emit").asInt()).isEqualTo(3);
    }

    @Test
    void searchesIndependentFlowsWithServerSidePagination() throws Exception {
        String prefix = "flowPage" + Long.toString(System.nanoTime());
        String firstCode = prefix + "_a";
        for (String code : new String[]{firstCode, prefix + "Xa", prefix + "b"}) {
            data(request("POST", "/api/flows", "{\"flowCode\":\"" + code
                    + "\",\"flowName\":\"分页流程\"}"));
        }
        String graph = json.writeValueAsString(FlowPublicationServiceTest.simpleGraph(1));
        data(request("PUT", "/api/flows/" + firstCode + "/draft?expectedRevision=0", graph));
        data(request("POST", "/api/flows/" + firstCode + "/publish", "{\"expectedRevision\":1}"));

        JsonNode firstPage = data(request("GET", "/api/flows?keyword=" + prefix + "&page=1&pageSize=2", null));
        assertThat(firstPage.get("total").asLong()).isEqualTo(3);
        assertThat(firstPage.get("records").size()).isEqualTo(2);
        assertThat(firstPage.get("page").asInt()).isEqualTo(1);
        assertThat(firstPage.get("pageSize").asInt()).isEqualTo(2);

        JsonNode secondPage = data(request("GET", "/api/flows?keyword=" + prefix + "&page=2&pageSize=2", null));
        assertThat(secondPage.get("records").size()).isEqualTo(1);
        JsonNode literalUnderscore = data(request("GET", "/api/flows?keyword=" + firstCode
                + "&page=1&pageSize=10", null));
        assertThat(literalUnderscore.get("total").asLong()).isEqualTo(1);
        assertThat(literalUnderscore.get("records").get(0).get("flowCode").asText()).isEqualTo(firstCode);
        assertThat(literalUnderscore.get("records").get(0).get("publishedVersionNumber").asLong()).isEqualTo(1);
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("data");
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
