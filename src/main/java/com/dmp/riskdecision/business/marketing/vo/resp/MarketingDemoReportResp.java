package com.dmp.riskdecision.business.marketing.vo.resp;

import com.dmp.riskdecision.business.marketing.model.DemoGroup;
import com.dmp.riskdecision.business.marketing.model.GroupMetrics;
import com.dmp.riskdecision.business.marketing.model.MarketingDemoReport;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** 分组利润报告及逐笔决策与反馈。 */
@Getter
public class MarketingDemoReportResp {
    private final String experimentId;
    private final Map<DemoGroup, GroupMetrics> groups;
    private final List<DemoCheckoutResp> attempts;

    public MarketingDemoReportResp(MarketingDemoReport report) {
        experimentId = report.getExperimentId();
        groups = report.getGroups();
        attempts = report.getAttempts().stream().map(DemoCheckoutResp::new).toList();
    }
}
