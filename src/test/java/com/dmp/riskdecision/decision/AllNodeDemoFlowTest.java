package com.dmp.riskdecision.decision;

import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import jakarta.annotation.Resource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** 用前端同一份全节点示例验证发布及条件分支执行。 */
@SpringBootTest
class AllNodeDemoFlowTest {
    @Resource
    private FlowAssetService flows;
    @Resource
    private SceneService scenes;
    @Resource
    private DecisionExecutionService decisions;
    @Resource
    private ObjectMapper json;

    @Test
    void publishesAllNodeGraphAndExecutesBothConditionBranches() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String flowCode = "allNodes" + suffix;
        String sceneCode = "allNodeScene" + suffix;
        GraphDefinition graph = json.readValue(Files.readString(
                Path.of("web/src/flow/all-node-example.json")), GraphDefinition.class);

        flows.create(flowCode, "全节点集成测试");
        assertThat(flows.validate(flowCode, graph).isValid()).isTrue();
        flows.saveDraft(flowCode, graph, 0);
        flows.publish(flowCode, 1);
        scenes.create(sceneCode, "全节点场景", null);
        scenes.bindFlow(sceneCode, flowCode);

        ExecutionResult positive = decisions.evaluate("all-nodes-true", sceneCode, Map.of("positive", true));
        assertThat(positive.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(positive.getNodeResults()).containsKeys(
                "prepare", "parallelLeft", "parallelRight", "truePath", "finalize");
        assertThat(positive.getNodeResults()).doesNotContainKey("falsePath");
        assertThat(positive.getBusinessResult().getConclusion()).isEqualTo("LOW_VALUE_OFFER");
        assertThat(positive.getTrace()).extracting("nodeId").contains("trueResult").doesNotContain("falseResult");

        ExecutionResult negative = decisions.evaluate("all-nodes-false", sceneCode, Map.of("positive", false));
        assertThat(negative.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(negative.getNodeResults()).containsKeys(
                "prepare", "parallelLeft", "parallelRight", "falsePath", "finalize");
        assertThat(negative.getNodeResults()).doesNotContainKey("truePath");
        assertThat(negative.getBusinessResult().getConclusion()).isEqualTo("NO_NEW_OFFER");
        assertThat(negative.getTrace()).extracting("nodeId").contains("falseResult").doesNotContain("trueResult");
    }
}
