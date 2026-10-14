package com.dmp.riskdecision.business.marketing.model;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 模拟实验的逐笔反馈和分组利润报告。 */
@Data
@AllArgsConstructor
public class MarketingDemoReport {
    private String experimentId;
    private Map<DemoGroup, GroupMetrics> groups;
    private List<CheckoutAttempt> attempts;
}
