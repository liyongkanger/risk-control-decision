package com.dmp.riskdecision.business.marketing.service.impl;

import com.dmp.riskdecision.business.marketing.MarketingDemoFlows;
import com.dmp.riskdecision.business.marketing.model.MarketingDemoStatus;
import com.dmp.riskdecision.business.marketing.service.MarketingDemoStatusService;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import com.dmp.riskdecision.policy.model.FlowVersion;
import com.dmp.riskdecision.policy.model.SceneInfo;
import com.dmp.riskdecision.policy.service.FlowAssetService;
import com.dmp.riskdecision.policy.service.SceneService;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import com.dmp.riskdecision.rule.service.RuleSetService;
import jakarta.annotation.Resource;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** 从已发布不可变图追溯营销样板的当前运行链，不触发初始化或发布。 */
@Service
public class MarketingDemoStatusServiceImpl implements MarketingDemoStatusService {
    @Resource private RuleSetService rules;
    @Resource private FlowAssetService flows;
    @Resource private SceneService scenes;

    /** {@inheritDoc} 资产可能逐步建立，查询过程不将缺失状态视为服务端错误。 */
    @Override
    public MarketingDemoStatus get() {
        RuleSetVersion rule = currentRule();
        FlowVersion eligibility = currentFlow(MarketingDemoFlows.ELIGIBILITY_CODE);
        FlowVersion selection = currentFlow(MarketingDemoFlows.SELECTION_CODE);
        FlowVersion outer = currentFlow(MarketingDemoFlows.OUTER_CODE);
        Long eligibilityRule = pinnedRule(eligibility);
        Long outerEligibility = pinnedSubflow(outer, "eligibilityCall");
        Long outerSelection = pinnedSubflow(outer, "selectionCall");

        SceneInfo scene = currentScene();
        FlowVersion selectedOuter = scene == null ? null : selectedOuter();
        Long runningEligibilityId = pinnedSubflow(selectedOuter, "eligibilityCall");
        Long runningSelectionId = pinnedSubflow(selectedOuter, "selectionCall");
        FlowVersion runningEligibility = fixedFlow(runningEligibilityId, MarketingDemoFlows.ELIGIBILITY_CODE);
        FlowVersion runningSelection = fixedFlow(runningSelectionId, MarketingDemoFlows.SELECTION_CODE);
        Long runningRuleId = pinnedRule(runningEligibility);
        boolean validRule = fixedRule(runningRuleId);
        boolean initialized = rule != null && eligibility != null && selection != null && outer != null
                && scene != null && MarketingDemoFlows.OUTER_CODE.equals(scene.getFlowCode())
                && selectedOuter != null && runningEligibility != null && runningSelection != null && validRule;

        return MarketingDemoStatus.builder()
                .initialized(initialized)
                .ruleCurrentVersionId(id(rule))
                .eligibilityCurrentVersionId(id(eligibility))
                .eligibilityPinnedRuleVersionId(eligibilityRule)
                .selectionCurrentVersionId(id(selection))
                .outerCurrentVersionId(id(outer))
                .outerCurrentVersionNumber(outer == null ? null : outer.getVersionNumber())
                .outerPinnedEligibilityVersionId(outerEligibility)
                .outerPinnedSelectionVersionId(outerSelection)
                .sceneSelectedOuterVersionId(id(selectedOuter))
                .sceneSelectedOuterVersionNumber(selectedOuter == null ? null : selectedOuter.getVersionNumber())
                .runningEligibilityVersionId(runningEligibilityId)
                .runningSelectionVersionId(runningSelectionId)
                .runningRuleVersionId(runningRuleId)
                .build();
    }

    private RuleSetVersion currentRule() {
        try { return rules.currentVersion(MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE); }
        catch (NoSuchElementException missing) { return null; }
    }

    private FlowVersion currentFlow(String code) {
        try { return flows.getPublished(code); }
        catch (NoSuchElementException missing) { return null; }
    }

    private SceneInfo currentScene() {
        try { return scenes.get(MarketingDemoFlows.SCENE_CODE); }
        catch (NoSuchElementException missing) { return null; }
    }

    private FlowVersion selectedOuter() {
        try { return scenes.getSelectedFlowVersion(MarketingDemoFlows.SCENE_CODE); }
        catch (NoSuchElementException missing) { return null; }
    }

    private FlowVersion fixedFlow(Long id, String expectedCode) {
        if (id == null) return null;
        try {
            FlowVersion version = flows.getVersion(id);
            return expectedCode.equals(version.getFlowCode()) ? version : null;
        } catch (NoSuchElementException missing) { return null; }
    }

    private boolean fixedRule(Long id) {
        if (id == null) return false;
        try { return MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE.equals(rules.requireVersion(id).getCode()); }
        catch (NoSuchElementException missing) { return false; }
    }

    /** 规则版本只能来自已发布图中的执行规则集节点。 */
    private Long pinnedRule(FlowVersion flow) {
        if (flow == null || flow.getGraph() == null) return null;
        return flow.getGraph().getNodes().stream()
                .filter(node -> "execute-rule-set".equals(node.getComponentKey()))
                .map(node -> node.getConfig().get("ruleVersionId"))
                .filter(Number.class::isInstance)
                .map(value -> ((Number) value).longValue())
                .findFirst().orElse(null);
    }

    /** 子流程版本仅从预期调用节点读取，避免误取其他子流程。 */
    private Long pinnedSubflow(FlowVersion flow, String nodeId) {
        if (flow == null || flow.getGraph() == null) return null;
        for (GraphNode node : flow.getGraph().getNodes()) {
            if (nodeId.equals(node.getId()) && node.getKind() == NodeKind.SUBFLOW
                    && node.getConfig().get("versionId") instanceof Number) {
                return ((Number) node.getConfig().get("versionId")).longValue();
            }
        }
        return null;
    }

    private Long id(RuleSetVersion version) { return version == null ? null : version.getId(); }

    private Long id(FlowVersion version) { return version == null ? null : version.getId(); }
}
