package com.dmp.riskdecision.component.liteflow.service;

import java.util.Map;

/** LiteFlow 调用固定决策子流程的业务端口。 */
public interface SubflowInvoker {
    /** 使用指定不可变版本独立运行子流程并返回调用节点的结构化结果。 */
    Map<String, Object> invoke(String requestId, String flowCode, long versionId, Map<String, Object> input);
}
