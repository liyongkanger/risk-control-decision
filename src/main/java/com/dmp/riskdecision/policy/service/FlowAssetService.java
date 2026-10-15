package com.dmp.riskdecision.policy.service;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowInfo;
import com.dmp.riskdecision.policy.model.FlowPage;
import com.dmp.riskdecision.policy.model.FlowValidation;
import com.dmp.riskdecision.policy.model.FlowVersion;

/** 脱离场景管理流程资产、草稿和发布版本。 */
public interface FlowAssetService {
    /** 创建独立的决策流程资产。 */
    FlowInfo create(String flowCode, String flowName);

    /** 获取有效流程资产。 */
    FlowInfo get(String flowCode);

    /** 获取当前已发布版本的输入声明。 */
    FlowVersion getPublished(String flowCode);

    /** 按不可变版本 ID 读取流程；供场景运行链追溯，不能据此修改版本。 */
    FlowVersion getVersion(long versionId);

    /** 按流程码或名称分页搜索有效流程。 */
    FlowPage search(String keyword, int page, int pageSize);

    /** 保存流程草稿并检查修订号。 */
    FlowDraft saveDraft(String flowCode, GraphDefinition graph, long expectedRevision);

    /** 获取流程当前草稿。 */
    FlowDraft getDraft(String flowCode);

    /** 校验流程图是否可发布。 */
    FlowValidation validate(String flowCode, GraphDefinition graph);

    /** 创建不可变版本并切换流程发布指针。 */
    FlowVersion publish(String flowCode, long expectedRevision);
}
