package com.dmp.riskdecision.business.marketing.vo.resp;

import com.dmp.riskdecision.business.marketing.model.CheckoutAttempt;
import com.dmp.riskdecision.business.marketing.model.DemoGroup;
import com.dmp.riskdecision.business.marketing.model.MarketingRuleTrace;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;

/** 模拟结算决策和实际反馈快照。 */
@Getter
public class DemoCheckoutResp {
    private final String experimentId;
    private final String checkoutId;
    private final String userId;
    private final DemoGroup group;
    private final String executionId;
    private final String requestId;
    private final Instant decidedAt;
    private final long outerVersionId;
    private final Map<String, Long> subflowVersionIds;
    private final String ruleSetCode;
    private final Long ruleVersionId;
    private final String matchedRuleId;
    private final String ruleReasonCode;
    private final String decisionStatus;
    private final String decisionConclusion;
    private final String decisionReason;
    private final String recommendedOfferCode;
    private final boolean eligible;
    private final BigDecimal orderAmount;
    private final BigDecimal preDiscountMargin;
    private final BigDecimal existingOfferAmount;
    private final boolean settled;
    private final boolean paid;
    private final boolean refunded;
    private final String actualOfferCode;
    private final BigDecimal actualDiscountAmount;
    private final BigDecimal contributionProfit;

    public DemoCheckoutResp(CheckoutAttempt attempt) {
        experimentId = attempt.getExperimentId();
        checkoutId = attempt.getCheckoutId();
        userId = attempt.getUserId();
        group = attempt.getGroup();
        executionId = attempt.getExecutionId();
        requestId = attempt.getRequestId();
        decidedAt = attempt.getDecidedAt();
        outerVersionId = attempt.getOuterVersionId();
        subflowVersionIds = attempt.getSubflowVersionIds();
        MarketingRuleTrace trace = attempt.getRuleTrace();
        ruleSetCode = trace == null ? null : trace.getRuleSetCode();
        ruleVersionId = trace == null ? null : trace.getRuleVersionId();
        matchedRuleId = trace == null ? null : trace.getMatchedRuleId();
        ruleReasonCode = trace == null ? null : trace.getRuleReasonCode();
        decisionStatus = attempt.getDecisionStatus();
        decisionConclusion = attempt.getDecisionConclusion();
        decisionReason = attempt.getDecisionReason();
        recommendedOfferCode = attempt.getRecommendedOfferCode();
        eligible = attempt.isEligible();
        orderAmount = attempt.getOrderAmount();
        preDiscountMargin = attempt.getPreDiscountMargin();
        existingOfferAmount = attempt.getExistingOfferAmount();
        settled = attempt.isSettled();
        paid = attempt.isPaid();
        refunded = attempt.isRefunded();
        actualOfferCode = attempt.getActualOfferCode();
        actualDiscountAmount = attempt.getActualDiscountAmount();
        contributionProfit = attempt.getContributionProfit();
    }
}
