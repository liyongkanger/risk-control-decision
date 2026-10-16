package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import com.dmp.riskdecision.business.marketing.repository.MarketingDemoStore;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.rule.service.RuleSetService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
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
    @Resource private MarketingDemoStore store;
    @Resource private VariableService variables;
    @Resource private RuleSetService rules;

    @Test
    void setupPinsMarketingLocatorDependenciesThroughRequestVariables() {
        demo.setup();
        Map<String, Long> locatorVersions = Map.of(
                "uid", variables.current("uid").getId(),
                "checkoutId", variables.current("checkoutId").getId(),
                "experimentId", variables.current("experimentId").getId());
        locatorVersions.forEach((code, id) -> {
            VariableVersion variable = variables.requireVersion(id);
            assertThat(variable.getSourceType()).isEqualTo("REQUEST_INPUT");
            assertThat(variable.getVariableCode()).isEqualTo(code);
            assertThat(variable.getCategoryCode()).isEqualTo("uid".equals(code) ? "USER"
                    : "checkoutId".equals(code) ? "TRANSACTION" : "CAMPAIGN");
        });
        for (String code : new String[]{MarketingDemoFlows.ORDER_AMOUNT_VARIABLE,
                MarketingDemoFlows.MARGIN_VARIABLE, MarketingDemoFlows.BUDGET_VARIABLE}) {
            VariableVersion variable = variables.current(code);
            assertThat(variable.getDependencyVersionIds()).containsExactlyInAnyOrderEntriesOf(locatorVersions);
            assertThat(variable.getCategoryCode()).isEqualTo(MarketingDemoFlows.BUDGET_VARIABLE.equals(code)
                    ? "BUDGET" : "TRANSACTION");
        }
        assertThat(rules.currentVersion(MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE)
                .getDefinition().getInputs()).allSatisfy(input -> {
                    assertThat(input.getVariableBinding()).isNotNull();
                    assertThat(input.getVariableBinding().getVariableVersionId()).isPositive();
                });
    }

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
        String experiment = "flowGroupTest";
        store.stageCheckout(experiment, requestId, "sampleUser",
                new BigDecimal(eligible ? "120" : "80"), new BigDecimal("30"), BigDecimal.ZERO);
        ExecutionResult result = store.withCurrentFacts(() -> decisions.evaluate(requestId,
                MarketingDemoFlows.SCENE_CODE,
                Map.of("uid", "sampleUser", "checkoutId", requestId, "experimentId", experiment,
                        "inExperiment", inExperiment, "highGroup", highGroup)));
        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getBusinessResult().getConclusion()).isEqualTo(expected);
        assertThat(result.getNodeResults()).containsKeys("eligibilityCall", "selectionCall");
    }
}
