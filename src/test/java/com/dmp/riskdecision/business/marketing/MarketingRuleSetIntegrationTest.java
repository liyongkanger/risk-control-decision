package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.business.marketing.model.CheckoutAttempt;
import com.dmp.riskdecision.business.marketing.service.MarketingDemoService;
import com.dmp.riskdecision.business.marketing.service.MarketingDemoStatusService;
import com.dmp.riskdecision.business.marketing.vo.resp.DemoCheckoutResp;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.rule.model.RuleEvaluation;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import com.dmp.riskdecision.rule.service.RuleSetService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** 用营销样板验证后台规则集确实接入资格子流程。 */
@SpringBootTest
class MarketingRuleSetIntegrationTest {
    @Resource private MarketingDemoService demo;
    @Resource private FlowAssetService flows;
    @Resource private RuleSetService rules;
    @Resource private DecisionExecutionService decisions;
    @Resource private MarketingDemoStatusService status;

    @Test
    void eligibilityUsesPublishedRulesInsteadOfPrecomputedBoolean() {
        demo.setup();
        RuleSetVersion version = rules.requireVersion(status.get().getRunningRuleVersionId());
        assertThat(flows.getPublished(MarketingDemoFlows.ELIGIBILITY_CODE).getGraph().getNodes())
                .anySatisfy(node -> assertThat(node.getComponentKey()).isEqualTo("execute-rule-set"));
        RuleEvaluation denied = rules.evaluateVersion(version.getId(), Map.of(
                "orderAmount", new BigDecimal("80"), "marginAfterMaxOffer", new BigDecimal("12"),
                "remainingBudget", new BigDecimal("2000")));
        assertThat(denied.getReasonCode()).isEqualTo("ORDER_BELOW_THRESHOLD");
        Object rawCall = decisions.evaluate("rule-detail-" + System.nanoTime(), MarketingDemoFlows.SCENE_CODE,
                Map.of("orderAmount", new BigDecimal("80"), "marginAfterMaxOffer", new BigDecimal("12"),
                        "remainingBudget", new BigDecimal("2000"), "inExperiment", true, "highGroup", true))
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
    }
}
