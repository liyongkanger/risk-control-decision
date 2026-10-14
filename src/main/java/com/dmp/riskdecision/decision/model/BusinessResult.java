package com.dmp.riskdecision.decision.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 单条决策流程产出的业务结论、原因和可选建议。 */
@Getter
@EqualsAndHashCode
public class BusinessResult {
    private final String conclusion;
    private final String reasonCode;
    private final String reason;
    private final Map<String, Object> details;
    private final SuggestedAction suggestedAction;

    public BusinessResult(String conclusion, String reasonCode, String reason,
                          Map<String, Object> details, SuggestedAction suggestedAction) {
        if (conclusion == null || conclusion.isBlank()) {
            throw new IllegalArgumentException("business conclusion is required");
        }
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("business reason code is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("business reason is required");
        }
        this.conclusion = conclusion;
        this.reasonCode = reasonCode;
        this.reason = reason;
        this.details = details == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
        this.suggestedAction = suggestedAction;
    }
}
