package com.dmp.riskdecision.orchestrator.graph;

/** 流程图节点类型。 */
public enum NodeKind {
    /** 流程入口。 */ START,
    /** 流程出口。 */ END,
    /** 普通执行组件。 */ COMPONENT,
    /** 并行分支入口。 */ PARALLEL_SPLIT,
    /** 并行分支汇合。 */ PARALLEL_JOIN,
    /** 条件分支入口。 */ CONDITION,
    /** 条件分支汇合。 */ CONDITION_JOIN,
    /** 显式业务结果。 */ RESULT,
    /** 固定版本的决策子流程调用。 */ SUBFLOW
}
