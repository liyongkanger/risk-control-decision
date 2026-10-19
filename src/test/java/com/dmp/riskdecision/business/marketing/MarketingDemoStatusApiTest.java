package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.rule.model.RuleSetSnapshot;
import com.dmp.riskdecision.rule.service.RuleSetService;
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

/** 验证工作台状态读取无副作用，并区分最新规则与真正运行的版本。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketingDemoStatusApiTest {
    @Value("${local.server.port}") private int port;
    @Resource private RuleSetService rules;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void statusDoesNotInitializeAndTracksPinnedRuleAfterUpdate() throws Exception {
        JsonNode before = data(get("/api/demo/checkout-offer/status"));
        JsonNode repeated = data(get("/api/demo/checkout-offer/status"));
        assertThat(repeated).isEqualTo(before);

        assertThat(post("/api/demo/checkout-offer/setup").statusCode()).isEqualTo(200);
        JsonNode ready = data(get("/api/demo/checkout-offer/status"));
        assertThat(ready.get("initialized").asBoolean()).isTrue();
        long pinned = ready.get("runningRuleVersionId").asLong();
        assertThat(pinned).isPositive();
        assertThat(ready.get("eligibilityPinnedRuleVersionId").asLong()).isEqualTo(pinned);
        assertThat(ready.get("outerPinnedEligibilityVersionId").asLong())
                .isEqualTo(ready.get("runningEligibilityVersionId").asLong());

        RuleSetSnapshot draft = rules.get(MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE);
        rules.updateDraft(draft.getCode(), draft.getDraftRevision(), draft.getDefinition());
        rules.publish(draft.getCode(), draft.getDraftRevision() + 1);
        JsonNode pending = data(get("/api/demo/checkout-offer/status"));
        assertThat(pending.get("ruleCurrentVersionId").asLong()).isNotEqualTo(pinned);
        assertThat(pending.get("runningRuleVersionId").asLong()).isEqualTo(pinned);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode root = json.readTree(response.body());
        assertThat(root.get("code").asText()).isEqualTo("SUCCESS");
        return root.get("data");
    }
}
