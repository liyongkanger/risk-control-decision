package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.business.marketing.model.CheckoutAttempt;
import com.dmp.riskdecision.business.marketing.model.DemoGroup;
import com.dmp.riskdecision.business.marketing.repository.MarketingDemoStore;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 不同应用上下文的模拟结算反馈不相互泄露。 */
class MarketingDemoStoreIsolationTest {
    @Test
    void attemptRecordsRemainWithinOwningStore() {
        MarketingDemoStore first = new MarketingDemoStore();
        MarketingDemoStore second = new MarketingDemoStore();
        String experiment = "storeIsolation" + System.nanoTime();
        CheckoutAttempt attempt = new CheckoutAttempt(experiment, "checkoutA", "userA", DemoGroup.CONTROL,
                "requestA", 1, Map.of(), null, "SUCCEEDED", "NO_NEW_OFFER", "无新增优惠", "NONE",
                true, new BigDecimal("120"), new BigDecimal("30"), BigDecimal.ZERO);
        first.save(attempt);
        assertThat(first.find(experiment, "checkoutA")).contains(attempt);
        assertThat(second.find(experiment, "checkoutA")).isEmpty();
    }

    @Test
    void stagedFactsWithSameBusinessIdsRemainContextLocal() {
        MarketingDemoStore first = new MarketingDemoStore();
        MarketingDemoStore second = new MarketingDemoStore();
        String experiment = "sameIds" + System.nanoTime();
        first.stageCheckout(experiment, "checkoutA", "userA",
                new BigDecimal("120"), new BigDecimal("30"), BigDecimal.ZERO);
        second.stageCheckout(experiment, "checkoutA", "userA",
                new BigDecimal("80"), new BigDecimal("20"), BigDecimal.ZERO);
        assertThat(first.requireStaged(experiment, "checkoutA", "userA").getOrderAmount())
                .isEqualByComparingTo("120");
        assertThat(second.requireStaged(experiment, "checkoutA", "userA").getOrderAmount())
                .isEqualByComparingTo("80");
        assertThat(MarketingDemoStore.currentForThread()).isNull();
        second.withCurrentFacts(() -> {
            assertThat(MarketingDemoStore.currentForThread()).isSameAs(second);
            return null;
        });
        assertThat(MarketingDemoStore.currentForThread()).isNull();
    }
}
