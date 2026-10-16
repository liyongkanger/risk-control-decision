package com.dmp.riskdecision.business.marketing.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;

/** 一次模拟结算的决策快照与后续实际反馈。 */
@Getter
public class CheckoutAttempt {
    private final String experimentId;
    private final String checkoutId;
    private final String userId;
    private final DemoGroup group;
    private final String executionId;
    private final String requestId;
    private final Instant decidedAt;
    private final long outerVersionId;
    private final Map<String, Long> subflowVersionIds;
    private final MarketingRuleTrace ruleTrace;
    private final String decisionStatus;
    private final String decisionConclusion;
    private final String decisionReason;
    private final String recommendedOfferCode;
    private final boolean eligible;
    private final BigDecimal orderAmount;
    private final BigDecimal preDiscountMargin;
    private final BigDecimal existingOfferAmount;
    private boolean settled;
    private boolean paid;
    private boolean refunded;
    private String actualOfferCode = "NONE";
    private BigDecimal actualDiscountAmount = BigDecimal.ZERO.setScale(2);

    public CheckoutAttempt(String experimentId, String checkoutId, String userId, DemoGroup group,
                           String requestId, long outerVersionId, Map<String, Long> subflowVersionIds,
                           MarketingRuleTrace ruleTrace,
                           String decisionStatus, String decisionConclusion, String decisionReason,
                           String recommendedOfferCode, boolean eligible, BigDecimal orderAmount,
                           BigDecimal preDiscountMargin, BigDecimal existingOfferAmount) {
        this(experimentId, checkoutId, userId, group, null, requestId, outerVersionId, subflowVersionIds,
                ruleTrace, decisionStatus, decisionConclusion, decisionReason, recommendedOfferCode,
                eligible, orderAmount, preDiscountMargin, existingOfferAmount);
    }

    public CheckoutAttempt(String experimentId, String checkoutId, String userId, DemoGroup group,
                           String executionId, String requestId, long outerVersionId,
                           Map<String, Long> subflowVersionIds, MarketingRuleTrace ruleTrace,
                           String decisionStatus, String decisionConclusion, String decisionReason,
                           String recommendedOfferCode, boolean eligible, BigDecimal orderAmount,
                           BigDecimal preDiscountMargin, BigDecimal existingOfferAmount) {
        this.experimentId = experimentId;
        this.checkoutId = checkoutId;
        this.userId = userId;
        this.group = group;
        this.executionId = executionId;
        this.requestId = requestId;
        this.decidedAt = Instant.now();
        this.outerVersionId = outerVersionId;
        this.subflowVersionIds = Map.copyOf(subflowVersionIds);
        this.ruleTrace = ruleTrace;
        this.decisionStatus = decisionStatus;
        this.decisionConclusion = decisionConclusion;
        this.decisionReason = decisionReason;
        this.recommendedOfferCode = recommendedOfferCode;
        this.eligible = eligible;
        this.orderAmount = orderAmount;
        this.preDiscountMargin = preDiscountMargin;
        this.existingOfferAmount = existingOfferAmount;
    }

    /** 反馈实际支付和唯一使用的优惠。 */
    public void settle(boolean paid, String offerCode, BigDecimal discountAmount) {
        if (settled) throw new IllegalStateException("Checkout is already settled");
        this.settled = true;
        this.paid = paid;
        this.actualOfferCode = offerCode;
        this.actualDiscountAmount = discountAmount;
    }

    /** 全额退款后本次订单不再贡献利润。 */
    public void refund() {
        if (!settled || !paid || refunded) throw new IllegalStateException("Checkout cannot be refunded");
        refunded = true;
    }

    public BigDecimal getContributionProfit() {
        return !paid || refunded ? BigDecimal.ZERO.setScale(2)
                : preDiscountMargin.subtract(actualDiscountAmount).setScale(2);
    }

    public boolean sameRequest(String userId, BigDecimal orderAmount,
                               BigDecimal margin, BigDecimal existingOfferAmount) {
        return this.userId.equals(userId) && this.orderAmount.compareTo(orderAmount) == 0
                && this.preDiscountMargin.compareTo(margin) == 0
                && this.existingOfferAmount.compareTo(existingOfferAmount) == 0;
    }
}
