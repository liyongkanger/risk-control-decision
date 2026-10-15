package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.policy.model.FlowDraft;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import com.dmp.riskdecision.policy.model.SceneInfo;
import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleSetSnapshot;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import com.dmp.riskdecision.rule.service.RuleSetService;
import jakarta.annotation.Resource;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 使用现有发布服务初始化可编辑的营销样板流程与场景。 */
@Service
public class MarketingDemoFlowService {
    @Resource
    private FlowAssetService flows;
    @Resource
    private SceneService scenes;
    @Resource
    private RuleSetService rules;

    private final MarketingDemoFlows definitions = new MarketingDemoFlows();
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 重复初始化复用已有发布版本，并显式将演示场景选中外层当前版本。
     *
     * <p>此方法只管理固定演示命名空间，不覆盖已经发布的流程草稿。</p>
     *
     * @return 场景、外层流程及两个子流程的当前发布版本标识
     */
    public synchronized MarketingDemoSetup setup() {
        RuleSetVersion rule = ensureRuleSet();
        FlowVersion eligibility = ensurePublished(MarketingDemoFlows.ELIGIBILITY_CODE,
                "演示优惠资格决策", definitions.eligibilityGraph(rule.getId()));
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

    /** 兼容直接结算的首次使用；已有场景保持其选中版本，不隐式切换到外层最新发布版。 */
    public synchronized void ensureInitialized() {
        SceneInfo scene;
        try { scene = scenes.get(MarketingDemoFlows.SCENE_CODE); }
        catch (NoSuchElementException missing) { setup(); return; }
        if (!MarketingDemoFlows.OUTER_CODE.equals(scene.getFlowCode())) {
            throw new IllegalStateException("Marketing demo scene uses another entry flow");
        }
        try { scenes.getSelectedFlowVersion(MarketingDemoFlows.SCENE_CODE); }
        catch (NoSuchElementException missingVersion) { setup(); }
    }

    /**
     * 已发布流程直接复用；旧版固定样板只在草稿仍等于该发布图时升级。
     * 已存在的未发布草稿需人工处理，初始化不得覆盖或代替用户发布。
     */
    private FlowVersion ensurePublished(String code, String name, GraphDefinition graph) {
        try {
            flows.get(code);
        } catch (NoSuchElementException missing) {
            flows.create(code, name);
        }
        try {
            FlowVersion current = flows.getPublished(code);
            boolean oldEligibility = MarketingDemoFlows.ELIGIBILITY_CODE.equals(code)
                    && current.getGraph().getNodes().stream().noneMatch(node ->
                    "execute-rule-set".equals(node.getComponentKey()));
            boolean oldOuter = MarketingDemoFlows.OUTER_CODE.equals(code)
                    && current.getGraph().getRequiredInputs().contains("eligible");
            if (!oldEligibility && !oldOuter) return current;
            FlowDraft draft = flows.getDraft(code);
            if (!current.getGraph().equals(draft.getGraph())) {
                throw new IllegalStateException("Marketing demo draft has unpublished edits: " + code);
            }
            flows.saveDraft(code, graph, draft.getRevision());
            return flows.publish(code, draft.getRevision() + 1);
        } catch (NoSuchElementException unpublished) {
            try {
                flows.getDraft(code);
                throw new IllegalStateException("Marketing demo draft requires manual publication: " + code);
            } catch (NoSuchElementException noDraft) {
                flows.saveDraft(code, graph, 0);
                return flows.publish(code, 1);
            }
        }
    }

    /** 初始化可供后台继续编辑的固定营销资格规则集。 */
    private RuleSetVersion ensureRuleSet() {
        try { return rules.currentVersion(MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE); }
        catch (NoSuchElementException unpublished) {
            try {
                rules.get(MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE);
                throw new IllegalStateException("Marketing demo rule draft requires manual publication");
            } catch (NoSuchElementException missing) { /* 仅全新样板规则可自动初始化。 */ }
            RuleSetSnapshot draft = rules.create(MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE,
                    "演示优惠资格规则", sampleRules());
            return rules.publish(draft.getCode(), draft.getDraftRevision());
        }
    }

    private RuleDefinition sampleRules() {
        return json.readValue("""
                {"inputs":[{"name":"orderAmount","type":"DECIMAL"},
                           {"name":"marginAfterMaxOffer","type":"DECIMAL"},
                           {"name":"remainingBudget","type":"DECIMAL"}],
                 "resultCodes":["ELIGIBLE","INELIGIBLE"],
                 "rows":[
                   {"id":"order-threshold","priority":10,
                    "conditions":[{"input":"orderAmount","operator":"LT","expected":100}],
                    "result":{"resultCode":"INELIGIBLE","reasonCode":"ORDER_BELOW_THRESHOLD","reason":"订单金额不足 100 元"}},
                   {"id":"margin-floor","priority":20,
                    "conditions":[{"input":"marginAfterMaxOffer","operator":"LT","expected":10}],
                    "result":{"resultCode":"INELIGIBLE","reasonCode":"MARGIN_BELOW_FLOOR","reason":"预计贡献利润不足 10 元"}},
                   {"id":"budget-limit","priority":30,
                    "conditions":[{"input":"remainingBudget","operator":"LT","expected":18}],
                    "result":{"resultCode":"INELIGIBLE","reasonCode":"BUDGET_INSUFFICIENT","reason":"活动预算不足"}}
                 ],
                 "defaultResult":{"resultCode":"ELIGIBLE","reasonCode":"QUALIFIED","reason":"满足优惠资格"}}
                """, RuleDefinition.class);
    }
}
