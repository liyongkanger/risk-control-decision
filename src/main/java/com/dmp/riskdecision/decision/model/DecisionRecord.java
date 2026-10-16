package com.dmp.riskdecision.decision.model;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/** 一次场景入口决策的不可变执行快照。 */
@Data
public class DecisionRecord {
    private String executionId;
    private String requestId;
    private String sceneCode;
    private long versionId;
    private String status;
    private String conclusion;
    private String errorCode;
    private LocalDateTime createdAt;
    private Map<String, Object> input;
    private Map<String, Object> result;
    private String inputJson;
    private String resultJson;
}
