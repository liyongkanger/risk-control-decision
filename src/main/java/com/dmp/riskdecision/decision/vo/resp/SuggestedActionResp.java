package com.dmp.riskdecision.decision.vo.resp;

import java.util.Map;
import lombok.Data;
import lombok.ToString;

/** 对外展示的建议动作，不表示动作已执行。 */
@Data
public class SuggestedActionResp {
    private String code;
    @ToString.Exclude
    private Map<String, Object> parameters;
}
