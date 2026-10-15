package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import jakarta.annotation.Resource;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** 营销样板初始化必须创建可执行的两条子流程及外层流程。 */
@SpringBootTest
class MarketingDemoFlowServiceTest {
    @Resource private MarketingDemoFlowService demo;
    @Resource private FlowAssetService flows;
    @Resource private SceneService scenes;
    @Resource private DecisionExecutionService decisions;

    @Test
    void setupIsRepeatableAndThreeGroupsReturnDistinctConclusions() {
        MarketingDemoSetup first = demo.setup();
        MarketingDemoSetup again = demo.setup();
        assertThat(again.getEligibilityVersionId()).isEqualTo(first.getEligibilityVersionId());
        assertThat(again.getSelectionVersionId()).isEqualTo(first.getSelectionVersionId());
        assertThat(again.getOuterVersionId()).isEqualTo(first.getOuterVersionId());
        assertThat(flows.getPublished(MarketingDemoFlows.OUTER_CODE).getId())
                .isEqualTo(first.getOuterVersionId());
        assertThat(scenes.get(MarketingDemoFlows.SCENE_CODE).getFlowCode())
                .isEqualTo(MarketingDemoFlows.OUTER_CODE);

        assertConclusion("control", true, false, false, "NO_NEW_OFFER");
        assertConclusion("low", true, true, false, "LOW_VALUE_OFFER");
        assertConclusion("high", true, true, true, "HIGH_VALUE_OFFER");
        assertConclusion("not-qualified", false, true, true, "NO_NEW_OFFER");
    }

    private void assertConclusion(String requestId, boolean eligible, boolean inExperiment,
                                  boolean highGroup, String expected) {
        ExecutionResult result = decisions.evaluate(requestId, MarketingDemoFlows.SCENE_CODE,
                Map.of("orderAmount", eligible ? 120 : 80, "marginAfterMaxOffer", 12,
                        "remainingBudget", 2000, "inExperiment", inExperiment, "highGroup", highGroup));
        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getBusinessResult().getConclusion()).isEqualTo(expected);
        assertThat(result.getNodeResults()).containsKeys("eligibilityCall", "selectionCall");
    }
}
