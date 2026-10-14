package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import jakarta.annotation.Resource;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** 使用现有发布服务初始化可编辑的营销样板流程与场景。 */
@Service
public class MarketingDemoFlowService {
    @Resource
    private FlowAssetService flows;
    @Resource
    private SceneService scenes;

    private final MarketingDemoFlows definitions = new MarketingDemoFlows();

    /**
     * 重复初始化复用已有发布版本，并显式将演示场景选中外层当前版本。
     *
     * <p>此方法只管理固定演示命名空间，不覆盖已经发布的流程草稿。</p>
     *
     * @return 场景、外层流程及两个子流程的当前发布版本标识
     */
    public synchronized MarketingDemoSetup setup() {
        FlowVersion eligibility = ensurePublished(MarketingDemoFlows.ELIGIBILITY_CODE,
                "演示优惠资格决策", definitions.eligibilityGraph());
        FlowVersion selection = ensurePublished(MarketingDemoFlows.SELECTION_CODE,
                "演示优惠选择决策", definitions.selectionGraph());
        FlowVersion outer = ensurePublished(MarketingDemoFlows.OUTER_CODE,
                "演示结算优惠决策", definitions.outerGraph(eligibility.getId(), selection.getId()));
        try {
            scenes.get(MarketingDemoFlows.SCENE_CODE);
        } catch (NoSuchElementException missing) {
            scenes.create(MarketingDemoFlows.SCENE_CODE, "演示结算场景", "模拟营销闭环入口");
        }
        scenes.bindFlow(MarketingDemoFlows.SCENE_CODE, MarketingDemoFlows.OUTER_CODE);
        return new MarketingDemoSetup(MarketingDemoFlows.SCENE_CODE,
                MarketingDemoFlows.ELIGIBILITY_CODE, eligibility.getId(),
                MarketingDemoFlows.SELECTION_CODE, selection.getId(),
                MarketingDemoFlows.OUTER_CODE, outer.getId());
    }

    /**
     * 已发布流程直接复用；尚未发布时按当前草稿修订号保存固定图并发布。
     * 只在流程资产或草稿确实不存在时创建，避免覆盖用户已有版本。
     */
    private FlowVersion ensurePublished(String code, String name, GraphDefinition graph) {
        try {
            flows.get(code);
        } catch (NoSuchElementException missing) {
            flows.create(code, name);
        }
        try {
            return flows.getPublished(code);
        } catch (NoSuchElementException unpublished) {
            long revision;
            try {
                FlowDraft draft = flows.getDraft(code);
                revision = draft.getRevision();
            } catch (NoSuchElementException noDraft) {
                revision = 0;
            }
            flows.saveDraft(code, graph, revision);
            return flows.publish(code, revision + 1);
        }
    }
}
