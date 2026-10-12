package com.dmp.riskdecision.decision.service;

import com.dmp.riskdecision.decision.model.ExecutionResult;
import java.util.Map;

/** 根据场景码执行已发布的决策流程。 */
public interface DecisionExecutionService {
    /** 执行同步决策，本次请求固定使用选定的流程版本。 */
    ExecutionResult evaluate(String requestId, String sceneCode, Map<String, Object> input);
}
