package com.dmp.riskdecision.orchestrator.graph;

/** 节点执行失败后的处理策略。 */
public enum FailurePolicy {
    /** 立即终止本次流程。 */
    FAIL_FAST
}
