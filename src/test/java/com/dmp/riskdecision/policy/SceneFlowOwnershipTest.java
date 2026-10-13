package com.dmp.riskdecision.policy;

import com.dmp.riskdecision.policy.repository.SceneFlowRepository;
import com.dmp.riskdecision.policy.service.SceneService;
import jakarta.annotation.Resource;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证新场景只绑定流程资产，草稿和版本由流程资产拥有。 */
@SpringBootTest
class SceneFlowOwnershipTest {
    @Resource
    private SceneService scenes;
    @Resource
    private SceneFlowRepository flows;
    @Resource
    private JdbcTemplate jdbc;

    @Test
    void createsBoundFlowAndStoresDraftByFlowId() {
        String code = "owner" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        scenes.create(code, "流程归属测试", null);
        flows.saveDraft(code, FlowPublicationServiceTest.simpleGraph(1), 0);

        Map<String, Object> scene = jdbc.queryForMap("SELECT flow_id FROM tb_scene WHERE scene_code = ?", code);
        long flowId = ((Number) scene.get("flow_id")).longValue();
        assertThat(flowId).isPositive();
        assertThat(jdbc.queryForObject("SELECT flow_code FROM tb_flow WHERE id = ?", String.class, flowId))
                .isEqualTo(code);
        Map<String, Object> draft = jdbc.queryForMap("SELECT flow_id FROM tb_flow_draft WHERE flow_id = ?", flowId);
        assertThat(((Number) draft.get("flow_id")).longValue()).isEqualTo(flowId);
    }
}
