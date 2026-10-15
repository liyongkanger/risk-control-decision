package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.business.marketing.model.CheckoutAttempt;
import com.dmp.riskdecision.business.marketing.model.DemoGroup;
import com.dmp.riskdecision.business.marketing.model.GroupMetrics;
import com.dmp.riskdecision.business.marketing.model.MarketingDemoReport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 按稳定用户分组汇总模拟支付、退款、补贴和贡献利润。 */
@Component
public class MarketingDemoMetrics {
    public MarketingDemoReport summarize(String experimentId, List<CheckoutAttempt> attempts) {
        Map<DemoGroup, GroupMetrics> groups = new EnumMap<>(DemoGroup.class);
        for (DemoGroup group : DemoGroup.values()) {
            Set<String> users = new HashSet<>();
            Set<String> paidUsers = new HashSet<>();
            Set<String> everPaidUsers = new HashSet<>();
            Set<String> refundedUsers = new HashSet<>();
            Set<String> recommendedUsers = new HashSet<>();
            Set<String> usedRecommendedUsers = new HashSet<>();
            Map<String, BigDecimal> profitByUser = new HashMap<>();
            BigDecimal discount = BigDecimal.ZERO;
            for (CheckoutAttempt attempt : attempts) {
                if (attempt.getGroup() != group || !attempt.isEligible()
                        || !"SUCCEEDED".equals(attempt.getDecisionStatus())) continue;
                users.add(attempt.getUserId());
                if (attempt.isPaid()) everPaidUsers.add(attempt.getUserId());
                if (attempt.isPaid() && !attempt.isRefunded()) paidUsers.add(attempt.getUserId());
                if (attempt.isRefunded()) refundedUsers.add(attempt.getUserId());
                if (!"NONE".equals(attempt.getRecommendedOfferCode())) {
                    recommendedUsers.add(attempt.getUserId());
                    if (attempt.isPaid() && !attempt.isRefunded()
                            && attempt.getRecommendedOfferCode().equals(attempt.getActualOfferCode())) {
                        usedRecommendedUsers.add(attempt.getUserId());
                    }
                }
                if (attempt.isPaid() && !attempt.isRefunded()) {
                    discount = discount.add(attempt.getActualDiscountAmount());
                }
                profitByUser.merge(attempt.getUserId(), attempt.getContributionProfit(), BigDecimal::add);
            }
            BigDecimal total = profitByUser.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal average = users.isEmpty() ? BigDecimal.ZERO.setScale(2)
                    : total.divide(BigDecimal.valueOf(users.size()), 2, RoundingMode.HALF_UP);
            BigDecimal conversion = users.isEmpty() ? BigDecimal.ZERO.setScale(2)
                    : BigDecimal.valueOf(paidUsers.size() * 100L)
                            .divide(BigDecimal.valueOf(users.size()), 2, RoundingMode.HALF_UP);
            groups.put(group, new GroupMetrics(group, users.size(), paidUsers.size(), refundedUsers.size(),
                    discount.setScale(2), total.setScale(2), average, conversion, null,
                    percentage(refundedUsers.size(), everPaidUsers.size()),
                    percentage(usedRecommendedUsers.size(), recommendedUsers.size())));
        }
        GroupMetrics control = groups.get(DemoGroup.CONTROL);
        if (control.getUsers() > 0) {
            for (DemoGroup group : List.of(DemoGroup.LOW, DemoGroup.HIGH)) {
                GroupMetrics treatment = groups.get(group);
                if (treatment.getUsers() > 0) {
                    treatment.setIncrementalAverageContributionProfit(treatment.getAverageContributionProfit()
                            .subtract(control.getAverageContributionProfit()).setScale(2));
                }
            }
        }
        return new MarketingDemoReport(experimentId, Map.copyOf(groups), List.copyOf(attempts));
    }

    /** 统一将唯一用户口径的比率返回为百分数；没有分母时保留空值。 */
    private BigDecimal percentage(int numerator, int denominator) {
        return denominator == 0 ? null : BigDecimal.valueOf(numerator * 100L)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
