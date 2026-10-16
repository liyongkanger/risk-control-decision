package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.business.marketing.model.CheckoutAttempt;
import com.dmp.riskdecision.business.marketing.service.MarketingDemoService;
import com.dmp.riskdecision.business.marketing.service.MarketingDemoStatusService;
import com.dmp.riskdecision.business.marketing.repository.MarketingDemoStore;
import com.dmp.riskdecision.business.marketing.vo.resp.DemoCheckoutResp;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.component.variable.service.VariableResolutionException;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.rule.model.RuleEvaluation;
import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import com.dmp.riskdecision.rule.service.RuleSetService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用营销样板验证后台规则集确实接入资格子流程。 */
@SpringBootTest
class MarketingRuleSetIntegrationTest {
    @Resource private MarketingDemoService demo;
    @Resource private FlowAssetService flows;
    @Resource private RuleSetService rules;
    @Resource private DecisionExecutionService decisions;
    @Resource private MarketingDemoStatusService status;
    @Resource private MarketingDemoStore store;

    @Test
    void eligibilityUsesPublishedRulesInsteadOfPrecomputedBoolean() {
        demo.setup();
        RuleSetVersion version = rules.requireVersion(status.get().getRunningRuleVersionId());
        assertThat(flows.getPublished(MarketingDemoFlows.ELIGIBILITY_CODE).getGraph().getNodes())
                .anySatisfy(node -> assertThat(node.getComponentKey()).isEqualTo("execute-rule-set"));
        assertThat(flows.getPublished(MarketingDemoFlows.ELIGIBILITY_CODE).getGraph().getNodes()
                .stream().filter(node -> "get-variable".equals(node.getComponentKey())).count()).isZero();
        assertThat(version.getDefinition().getInputs()).allSatisfy(input ->
                assertThat(input.getVariableBinding()).isNotNull());
        assertThat(flows.getPublished(MarketingDemoFlows.ELIGIBILITY_CODE).getGraph().getRequiredInputs())
                .containsExactly("uid", "checkoutId", "experimentId");
        assertThat(flows.getPublished(MarketingDemoFlows.OUTER_CODE).getGraph().getRequiredInputs())
                .doesNotContain("orderAmount", "marginAfterMaxOffer", "remainingBudget");
        String checkoutId = "ruleDetail" + System.nanoTime();
        String experiment = "ruleTrace" + System.nanoTime();
        store.stageCheckout(experiment, checkoutId, "ruleUser",
                new BigDecimal("80"), new BigDecimal("30"), BigDecimal.ZERO);
        RuleEvaluation denied = store.withCurrentFacts(() -> rules.evaluateVersion(version.getId(),
                Map.of("uid", "ruleUser", "checkoutId", checkoutId, "experimentId", experiment)));
        assertThat(denied.getReasonCode()).isEqualTo("ORDER_BELOW_THRESHOLD");
        Object rawCall = store.withCurrentFacts(() -> decisions.evaluate(
                "rule-detail-" + System.nanoTime(), MarketingDemoFlows.SCENE_CODE,
                Map.of("uid", "ruleUser", "checkoutId", checkoutId, "experimentId", experiment,
                        "inExperiment", true, "highGroup", true)))
                .getNodeResults().get("eligibilityCall");
        Map<?, ?> child = (Map<?, ?>) rawCall;
        Map<?, ?> business = (Map<?, ?>) child.get("businessResult");
        Map<?, ?> details = (Map<?, ?>) business.get("details");
        assertThat(details.get("ruleVersionId")).isEqualTo(version.getId());
        assertThat(details.get("matchedRuleId")).isEqualTo("order-threshold");
        CheckoutAttempt checkout = demo.checkout("ruleDemo" + System.nanoTime(), "order-1", "user-1",
                new BigDecimal("80"), new BigDecimal("30"), BigDecimal.ZERO);
        assertThat(checkout.isEligible()).isFalse();
        assertThat(checkout.getDecisionConclusion()).isEqualTo("NO_NEW_OFFER");
        assertThat(checkout.getDecisionReason()).isEqualTo("订单金额不足 100 元");
        DemoCheckoutResp visible = new DemoCheckoutResp(checkout);
        assertThat(visible.getRuleSetCode()).isEqualTo(MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE);
        assertThat(visible.getRuleVersionId()).isEqualTo(version.getId());
        assertThat(visible.getMatchedRuleId()).isEqualTo("order-threshold");
        assertThat(visible.getRuleReasonCode()).isEqualTo("ORDER_BELOW_THRESHOLD");

        String missingCheckout = "missingCheckout" + System.nanoTime();
        com.dmp.riskdecision.decision.model.ExecutionResult missing = decisions.evaluate(
                "missing-facts-" + System.nanoTime(), MarketingDemoFlows.SCENE_CODE,
                Map.of("uid", "ruleUser", "checkoutId", missingCheckout, "experimentId", experiment,
                        "inExperiment", true, "highGroup", true));
        assertThat(missing.getStatus()).isEqualTo("FAILED");
        assertThat(missing.getBusinessResult()).isNull();
    }

    @Test
    void ruleRejectsMismatchedVariableAndMissingContext() {
        demo.setup();
        RuleSetVersion version = rules.requireVersion(status.get().getRunningRuleVersionId());
        RuleDefinition draft = new ObjectMapper().readValue(
                new ObjectMapper().writeValueAsString(version.getDefinition()), RuleDefinition.class);
        draft.getInputs().get(0).getVariableBinding().setVariableVersionId(
                draft.getInputs().get(1).getVariableBinding().getVariableVersionId());
        assertThatThrownBy(() -> rules.create("invalidBinding" + System.nanoTime(), "无效绑定", draft))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("binding");
        assertThatThrownBy(() -> rules.evaluateVersion(version.getId(), Map.of("uid", "u1")))
                .isInstanceOf(VariableResolutionException.class)
                .hasMessageContaining("checkoutId");
    }
}
