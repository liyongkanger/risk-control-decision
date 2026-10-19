package com.dmp.riskdecision.business.marketing.vo.resp;

import com.dmp.riskdecision.business.marketing.model.MarketingDemoStatus;
import lombok.Getter;

/** 工作台展示的营销策略当前版本和场景真实运行链。 */
@Getter
public class MarketingDemoStatusResp {
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

    public MarketingDemoStatusResp(MarketingDemoStatus status) {
        initialized = status.isInitialized();
        ruleCurrentVersionId = status.getRuleCurrentVersionId();
        eligibilityCurrentVersionId = status.getEligibilityCurrentVersionId();
        eligibilityPinnedRuleVersionId = status.getEligibilityPinnedRuleVersionId();
        selectionCurrentVersionId = status.getSelectionCurrentVersionId();
        outerCurrentVersionId = status.getOuterCurrentVersionId();
        outerCurrentVersionNumber = status.getOuterCurrentVersionNumber();
        outerPinnedEligibilityVersionId = status.getOuterPinnedEligibilityVersionId();
        outerPinnedSelectionVersionId = status.getOuterPinnedSelectionVersionId();
        sceneSelectedOuterVersionId = status.getSceneSelectedOuterVersionId();
        sceneSelectedOuterVersionNumber = status.getSceneSelectedOuterVersionNumber();
        runningEligibilityVersionId = status.getRunningEligibilityVersionId();
        runningSelectionVersionId = status.getRunningSelectionVersionId();
        runningRuleVersionId = status.getRunningRuleVersionId();
    }
}
