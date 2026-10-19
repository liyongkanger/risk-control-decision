package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.business.marketing.model.CheckoutAttempt;
import com.dmp.riskdecision.business.marketing.service.MarketingDemoService;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** 新外层流程发布后，模拟结算只在显式选用后切换场景运行版本。 */
@SpringBootTest
class MarketingDemoActivationTest {
    @Resource private MarketingDemoService demo;
    @Resource private FlowAssetService flows;
    @Resource private SceneService scenes;

    @Test
    void checkoutAndFixtureKeepSelectedVersionUntilSetupIsCalled() {
        demo.setup();
        long selected = scenes.getSelectedFlowVersion(MarketingDemoFlows.SCENE_CODE).getId();
        FlowDraft draft = flows.getDraft(MarketingDemoFlows.OUTER_CODE);
        flows.saveDraft(MarketingDemoFlows.OUTER_CODE, draft.getGraph(), draft.getRevision());
        FlowVersion next = flows.publish(MarketingDemoFlows.OUTER_CODE, draft.getRevision() + 1);
        assertThat(next.getId()).isNotEqualTo(selected);

        CheckoutAttempt checkout = demo.checkout("activationA", "orderA", "userA",
                new BigDecimal("120"), new BigDecimal("30"), BigDecimal.ZERO);
        assertThat(checkout.getOuterVersionId()).isEqualTo(selected);
        assertThat(demo.runFixture("activationB").getAttempts())
                .allSatisfy(item -> assertThat(item.getOuterVersionId()).isEqualTo(selected));

        demo.setup();
        assertThat(scenes.getSelectedFlowVersion(MarketingDemoFlows.SCENE_CODE).getId()).isEqualTo(next.getId());
        CheckoutAttempt later = demo.checkout("activationC", "orderC", "userC",
                new BigDecimal("120"), new BigDecimal("30"), BigDecimal.ZERO);
        assertThat(later.getOuterVersionId()).isEqualTo(next.getId());
    }
}
