package com.dmp.riskdecision.business.marketing.vo.resp;

import com.dmp.riskdecision.business.marketing.MarketingDemoSetup;
import lombok.Getter;

/** 已初始化演示场景和流程版本。 */
@Getter
public class MarketingDemoSetupResp {
    private final String sceneCode;
    private final String eligibilityFlowCode;
    private final long eligibilityVersionId;
    private final String selectionFlowCode;
    private final long selectionVersionId;
    private final String outerFlowCode;
    private final long outerVersionId;

    public MarketingDemoSetupResp(MarketingDemoSetup setup) {
        sceneCode = setup.getSceneCode();
        eligibilityFlowCode = setup.getEligibilityFlowCode();
        eligibilityVersionId = setup.getEligibilityVersionId();
        selectionFlowCode = setup.getSelectionFlowCode();
        selectionVersionId = setup.getSelectionVersionId();
        outerFlowCode = setup.getOuterFlowCode();
        outerVersionId = setup.getOuterVersionId();
    }
}
