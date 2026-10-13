package com.dmp.riskdecision.policy.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 可脱离业务场景独立管理的流程资产。 */
@Getter
@AllArgsConstructor
public class FlowInfo {
    private final long id;
    private final String flowCode;
    private final String flowName;
    private final String flowType;
    private final Long publishedVersionId;
    private final Long publishedVersionNumber;
    private final LocalDateTime updatetime;
}
