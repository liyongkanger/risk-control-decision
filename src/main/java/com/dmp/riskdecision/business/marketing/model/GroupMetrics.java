package com.dmp.riskdecision.business.marketing.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 单个演示组在同一观察窗口内的实际反馈指标。 */
@Data
@AllArgsConstructor
public class GroupMetrics {
    private DemoGroup group;
    private int users;
    private int paidUsers;
    private int refundedUsers;
    private BigDecimal merchantDiscount;
    private BigDecimal totalContributionProfit;
    private BigDecimal averageContributionProfit;
    private BigDecimal paymentConversionRate;
}
