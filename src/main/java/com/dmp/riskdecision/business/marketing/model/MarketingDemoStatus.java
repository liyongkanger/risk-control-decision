package com.dmp.riskdecision.business.marketing.model;

import lombok.Builder;
import lombok.Getter;

/** 营销样板当前资产版本与场景实际运行链的只读快照。 */
@Getter
@Builder
public class MarketingDemoStatus {
    private final boolean initialized;
    private final Long ruleCurrentVersionId;
    private final Long eligibilityCurrentVersionId;
    private final Long eligibilityPinnedRuleVersionId;
    private final Long selectionCurrentVersionId;
    private final Long outerCurrentVersionId;
    private final Long outerCurrentVersionNumber;
    private final Long outerPinnedEligibilityVersionId;
    private final Long outerPinnedSelectionVersionId;
    private final Long sceneSelectedOuterVersionId;
    private final Long sceneSelectedOuterVersionNumber;
    private final Long runningEligibilityVersionId;
    private final Long runningSelectionVersionId;
    private final Long runningRuleVersionId;
}
