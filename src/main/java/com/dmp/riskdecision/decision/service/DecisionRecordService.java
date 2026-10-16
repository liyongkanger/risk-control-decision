package com.dmp.riskdecision.decision.service;

import com.dmp.riskdecision.decision.model.DecisionRecord;
import com.dmp.riskdecision.decision.model.DecisionRecordPage;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import java.util.Map;

/** 保存和查询场景入口的决策执行快照。 */
public interface DecisionRecordService {
    /** 保存一次已进入执行阶段的结果，返回与 requestId 独立的执行标识。 */
    String save(ExecutionResult result, Map<String, Object> input);

    /** 按执行标识读取完整快照；不存在时抛出资源不存在异常。 */
    DecisionRecord get(String executionId);

    /** 按场景、请求标识和状态精确筛选，按记录 ID 倒序分页。 */
    DecisionRecordPage search(String sceneCode, String requestId, String status, int page, int pageSize);
}
