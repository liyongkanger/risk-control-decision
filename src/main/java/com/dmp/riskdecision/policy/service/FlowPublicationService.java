package com.dmp.riskdecision.policy.service;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowValidation;
import com.dmp.riskdecision.policy.model.FlowVersion;

/** 场景流程草稿、校验与发布用例。 */
public interface FlowPublicationService {
    /** 保存指定场景的草稿，并检查预期修订号。 */
    FlowDraft saveDraft(String sceneCode, GraphDefinition graph, long expectedRevision);

    /** 读取指定场景的草稿。 */
    FlowDraft getDraft(String sceneCode);

    /** 校验图定义是否可以发布。 */
    FlowValidation validate(String sceneCode, GraphDefinition graph);

    /** 发布指定修订号的草稿。 */
    FlowVersion publish(String sceneCode, long expectedRevision);
}
