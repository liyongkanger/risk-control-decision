package com.dmp.riskdecision.business.marketing;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 营销演示已初始化的流程和场景版本标识。 */
@Data
@AllArgsConstructor
public class MarketingDemoSetup {
    private String sceneCode;
    private String eligibilityFlowCode;
    private long eligibilityVersionId;
    private String selectionFlowCode;
    private long selectionVersionId;
    private String outerFlowCode;
    private long outerVersionId;
}
