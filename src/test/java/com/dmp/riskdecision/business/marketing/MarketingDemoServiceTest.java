package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.business.marketing.model.CheckoutAttempt;
import com.dmp.riskdecision.business.marketing.model.DemoGroup;
import com.dmp.riskdecision.business.marketing.model.MarketingDemoReport;
import com.dmp.riskdecision.business.marketing.service.MarketingDemoService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 固定实验利润、反馈幂等及退款修正验收。 */
@SpringBootTest
class MarketingDemoServiceTest {
    @Resource private MarketingDemoService demo;

    @Test
    void fixedThirtyUserFixtureShowsProfitBeyondConversion() {
        String experiment = "fixture" + System.nanoTime();
        MarketingDemoReport first = demo.runFixture(experiment);
        MarketingDemoReport repeated = demo.runFixture(experiment);

        assertThat(first.getAttempts()).hasSize(30);
        assertThat(repeated.getAttempts()).hasSize(30);
        assertGroup(first, DemoGroup.CONTROL, 10, 2, "6.00", "20.00");
        assertGroup(first, DemoGroup.LOW, 10, 3, "7.50", "30.00");
        assertGroup(first, DemoGroup.HIGH, 10, 4, "4.80", "40.00");
        assertThat(first.getGroups().get(DemoGroup.CONTROL).getIncrementalAverageContributionProfit()).isNull();
        assertThat(first.getGroups().get(DemoGroup.LOW).getIncrementalAverageContributionProfit())
                .isEqualByComparingTo("1.50");
        assertThat(first.getGroups().get(DemoGroup.HIGH).getIncrementalAverageContributionProfit())
                .isEqualByComparingTo("-1.20");
        assertThat(first.getGroups().get(DemoGroup.LOW).getRecommendedOfferUseRate())
                .isEqualByComparingTo("30.00");
        assertThat(first.getGroups().get(DemoGroup.HIGH).getRecommendedOfferUseRate())
                .isEqualByComparingTo("40.00");
        assertThat(first.getAttempts()).allSatisfy(attempt -> {
            assertThat(attempt.getDecisionStatus()).isEqualTo("SUCCEEDED");
            assertThat(attempt.getOuterVersionId()).isPositive();
            assertThat(attempt.getSubflowVersionIds()).containsKeys("eligibilityCall", "selectionCall");
        });
    }

    @Test
    void actualExistingOfferAndRefundReplaceRecommendationWithoutDuplicatingEvents() {
        String experiment = "feedback" + System.nanoTime();
        demo.setup();
        CheckoutAttempt checkout = demo.checkout(experiment, "checkout-1", "stable-user",
                amount("120.00"), amount("30.00"), amount("3.00"));
        CheckoutAttempt sameUser = demo.checkout(experiment, "checkout-2", "stable-user",
                amount("120.00"), amount("30.00"), amount("3.00"));
        assertThat(sameUser.getGroup()).isEqualTo(checkout.getGroup());
        assertThat(checkout.getRecommendedOfferCode()).isNotNull();

        CheckoutAttempt settled = demo.feedback(experiment, "checkout-1", "settle-1",
                "SETTLED", true, "EXISTING");
        assertThat(settled.getActualOfferCode()).isEqualTo("EXISTING");
        assertThat(settled.getActualDiscountAmount()).isEqualByComparingTo("3.00");
        assertThat(settled.getContributionProfit()).isEqualByComparingTo("27.00");
        assertThat(demo.feedback(experiment, "checkout-1", "settle-1",
                "SETTLED", true, "EXISTING").getContributionProfit()).isEqualByComparingTo("27.00");
        assertThatThrownBy(() -> demo.feedback(experiment, "checkout-1", "settle-1",
                "SETTLED", true, "NONE")).isInstanceOf(IllegalStateException.class);

        CheckoutAttempt refunded = demo.feedback(experiment, "checkout-1", "refund-1",
                "REFUNDED", null, null);
        assertThat(refunded.getContributionProfit()).isEqualByComparingTo("0.00");
        assertThat(demo.feedback(experiment, "checkout-1", "refund-1",
                "REFUNDED", null, null).getContributionProfit()).isEqualByComparingTo("0.00");
        assertThat(demo.report(experiment).getGroups().get(checkout.getGroup()).getPaidUsers()).isZero();
        assertThat(demo.report(experiment).getGroups().get(checkout.getGroup()).getMerchantDiscount())
                .isEqualByComparingTo("0.00");
        assertThat(demo.report(experiment).getGroups().get(checkout.getGroup()).getRefundRate())
                .isEqualByComparingTo("100.00");
    }

    @Test
    void unqualifiedCheckoutReturnsNormalNoOfferAndUnpaidFeedbackAddsZeroProfit() {
        String experiment = "unqualified" + System.nanoTime();
        demo.setup();
        CheckoutAttempt attempt = demo.checkout(experiment, "checkout-1", "user-no-order",
                amount("80.00"), amount("30.00"), amount("0.00"));
        assertThat(attempt.isEligible()).isFalse();
        assertThat(attempt.getDecisionConclusion()).isEqualTo("NO_NEW_OFFER");
        CheckoutAttempt settled = demo.feedback(experiment, "checkout-1", "settle-1",
                "SETTLED", false, "NONE");
        assertThat(settled.getContributionProfit()).isEqualByComparingTo("0.00");
        assertThat(demo.report(experiment).getGroups().values())
                .allSatisfy(group -> assertThat(group.getUsers()).isZero());
    }

    @Test
    void feedbackEventIdsAreScopedToCheckout() {
        String experiment = "eventScope" + System.nanoTime();
        demo.checkout(experiment, "orderA", "userA", amount("120.00"),
                amount("30.00"), amount("0.00"));
        demo.checkout(experiment, "orderB", "userB", amount("120.00"),
                amount("30.00"), amount("0.00"));
        assertThat(demo.feedback(experiment, "orderA", "paidEvent", "SETTLED", true, "NONE")
                .getContributionProfit()).isEqualByComparingTo("30.00");
        assertThat(demo.feedback(experiment, "orderB", "paidEvent", "SETTLED", true, "NONE")
                .getContributionProfit()).isEqualByComparingTo("30.00");
    }

    @Test
    void emptyExperimentHasNoIncrementalOrRateDenominators() {
        MarketingDemoReport empty = demo.report("empty" + System.nanoTime());
        assertThat(empty.getGroups().get(DemoGroup.LOW).getIncrementalAverageContributionProfit()).isNull();
        assertThat(empty.getGroups().get(DemoGroup.LOW).getRefundRate()).isNull();
        assertThat(empty.getGroups().get(DemoGroup.LOW).getRecommendedOfferUseRate()).isNull();
    }

    private static void assertGroup(MarketingDemoReport report, DemoGroup group, int users,
                                    int paid, String profit, String conversion) {
        assertThat(report.getGroups().get(group).getUsers()).isEqualTo(users);
        assertThat(report.getGroups().get(group).getPaidUsers()).isEqualTo(paid);
        assertThat(report.getGroups().get(group).getAverageContributionProfit())
                .isEqualByComparingTo(profit);
        assertThat(report.getGroups().get(group).getPaymentConversionRate())
                .isEqualByComparingTo(conversion);
    }

    private static BigDecimal amount(String value) { return new BigDecimal(value); }
}
