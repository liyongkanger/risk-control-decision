package com.dmp.riskdecision.decision.vo.resp;

import java.util.Map;
import lombok.Data;
import lombok.ToString;

/** 决策流程对外返回的业务结论与原因。 */
@Data
public class BusinessResultResp {
    private String conclusion;
    private String reasonCode;
    private String reason;
    @ToString.Exclude
    private Map<String, Object> details;
    private SuggestedActionResp suggestedAction;
}
