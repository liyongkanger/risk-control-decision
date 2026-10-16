package com.dmp.riskdecision.decision.vo.resp;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

/** 单次决策执行记录；列表不包含 input 和 result 大字段。 */
@Data
public class DecisionRecordResp {
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
