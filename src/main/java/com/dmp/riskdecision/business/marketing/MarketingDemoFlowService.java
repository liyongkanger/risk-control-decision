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
import com.dmp.riskdecision.rule.model.RuleVariableBinding;
import com.dmp.riskdecision.rule.service.RuleSetService;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableService;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
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
    @Resource
    private VariableService variables;

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
        Map<String, Long> locators = Map.of(
                "uid", ensureRequestVariable("uid").getId(),
                "checkoutId", ensureRequestVariable("checkoutId").getId(),
                "experimentId", ensureRequestVariable("experimentId").getId());
        VariableVersion orderAmount = ensureVariable(MarketingDemoFlows.ORDER_AMOUNT_VARIABLE,
                "演示订单金额", "marketing://checkout", "orderAmount",
                List.of("uid", "checkoutId", "experimentId"), locators);
        VariableVersion margin = ensureVariable(MarketingDemoFlows.MARGIN_VARIABLE,
                "演示预计优惠后毛利", "marketing://checkout", "marginAfterMaxOffer",
                List.of("uid", "checkoutId", "experimentId"), locators);
        VariableVersion budget = ensureVariable(MarketingDemoFlows.BUDGET_VARIABLE,
                "演示活动剩余预算", "marketing://budget", "remainingBudget",
                List.of("uid", "checkoutId", "experimentId"), locators);
        RuleSetVersion rule = ensureRuleSet(orderAmount, margin, budget);
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
        try {
            FlowVersion selected = scenes.getSelectedFlowVersion(MarketingDemoFlows.SCENE_CODE);
            if (selected.getGraph().getRequiredInputs().contains("orderAmount")
                    || hasLegacyQualification(selected)) setup();
        }
        catch (NoSuchElementException missingVersion) { setup(); }
    }

    /** 判断已选中外层流程是否仍固定旧金额直传资格流程或旧预算变量契约。 */
    private boolean hasLegacyQualification(FlowVersion selectedOuter) {
        if (selectedOuter.getGraph() == null || selectedOuter.getGraph().getNodes() == null) return false;
        for (com.dmp.riskdecision.orchestrator.graph.GraphNode node : selectedOuter.getGraph().getNodes()) {
            if (!"eligibilityCall".equals(node.getId()) || node.getConfig() == null
                    || !(node.getConfig().get("versionId") instanceof Number)) continue;
            FlowVersion eligibility = flows.getVersion(((Number) node.getConfig().get("versionId")).longValue());
            return eligibility.getGraph().getRequiredInputs().contains("orderAmount")
                    || eligibility.getGraph().getNodes().stream().anyMatch(item ->
                    "get-variable".equals(item.getComponentKey()));
        }
        return false;
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
                    && (current.getGraph().getRequiredInputs().contains("orderAmount")
                    || current.getGraph().getNodes().stream().noneMatch(node ->
                    "execute-rule-set".equals(node.getComponentKey()))
                    || current.getGraph().getNodes().stream().anyMatch(node ->
                    "get-variable".equals(node.getComponentKey())
                    || "execute-rule-set".equals(node.getComponentKey())
                    && (node.getConfig().containsKey("inputMappings")
                    || !sameNodeVersion(node, graph, "ruleVersionId"))));
            boolean oldOuter = MarketingDemoFlows.OUTER_CODE.equals(code)
                    && (current.getGraph().getRequiredInputs().contains("eligible")
                    || current.getGraph().getRequiredInputs().contains("orderAmount")
                    || hasLegacyQualification(current)
                    || current.getGraph().getNodes().stream().anyMatch(node ->
                    "eligibilityCall".equals(node.getId())
                    && !sameNodeVersion(node, graph, "versionId")));
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

    /** JSON 往返可能把较小版本 ID 解析为 Integer，按数值比较避免重复发布。 */
    private boolean sameNodeVersion(com.dmp.riskdecision.orchestrator.graph.GraphNode current,
                                    GraphDefinition expected, String field) {
        Object value = current.getConfig().get(field);
        return value instanceof Number && expected.getNodes().stream().anyMatch(node ->
                current.getId().equals(node.getId()) && node.getConfig().get(field) instanceof Number
                && ((Number) value).longValue() ==
                ((Number) node.getConfig().get(field)).longValue());
    }

    /** 初始化可供后台继续编辑的固定营销资格规则集。 */
    private RuleSetVersion ensureRuleSet(VariableVersion order, VariableVersion margin, VariableVersion budget) {
        try {
            RuleSetVersion current = rules.currentVersion(MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE);
            if (current.getDefinition() == null || current.getDefinition().getInputs().size() == 3
                    && matchesBinding(current.getDefinition().getInputs().get(0), order)
                    && matchesBinding(current.getDefinition().getInputs().get(1), margin)
                    && matchesBinding(current.getDefinition().getInputs().get(2), budget)) {
                return current;
            }
            RuleSetSnapshot draft = rules.get(current.getCode());
            if (!json.writeValueAsString(draft.getDefinition())
                    .equals(json.writeValueAsString(current.getDefinition()))) {
                throw new IllegalStateException("Marketing demo rule draft has unpublished edits");
            }
            RuleSetSnapshot updated = rules.updateDraft(current.getCode(), draft.getDraftRevision(),
                    sampleRules(order, margin, budget));
            return rules.publish(current.getCode(), updated.getDraftRevision());
        }
        catch (NoSuchElementException unpublished) {
            try {
                rules.get(MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE);
                throw new IllegalStateException("Marketing demo rule draft requires manual publication");
            } catch (NoSuchElementException missing) { /* 仅全新样板规则可自动初始化。 */ }
            RuleSetSnapshot draft = rules.create(MarketingDemoFlows.ELIGIBILITY_RULE_SET_CODE,
                    "演示优惠资格规则", sampleRules(order, margin, budget));
            return rules.publish(draft.getCode(), draft.getDraftRevision());
        }
    }

    /** 样板初次登记模拟来源变量；已有变量版本由后台维护，初始化不会覆盖。 */
    private VariableVersion ensureRequestVariable(String code) {
        try {
            VariableVersion current = variables.current(code);
            if (!"REQUEST_INPUT".equals(current.getSourceType())
                    || current.getValueType() != VariableValueType.STRING) {
                throw new IllegalStateException("Marketing locator variable conflicts with existing definition: " + code);
            }
            return current;
        } catch (NoSuchElementException missing) {
            return variables.create(code, code, VariableValueType.STRING, "REQUEST_INPUT",
                    null, 0, List.of(), null, Map.of(),
                    "uid".equals(code) ? "USER" : "checkoutId".equals(code) ? "TRANSACTION" : "CAMPAIGN");
        }
    }

    private VariableVersion ensureVariable(String code, String name, String sourceUrl,
                                           String resultPath, List<String> parameters,
                                           Map<String, Long> dependencyVersionIds) {
        try {
            VariableVersion current = variables.current(code);
            if ("DEMO_MARKETING".equals(current.getSourceType())
                    && sourceUrl.equals(current.getUrl()) && resultPath.equals(current.getResultPath())
                    && (current.getParameterNames().equals(parameters)
                    || MarketingDemoFlows.BUDGET_VARIABLE.equals(code)
                    && current.getParameterNames().equals(List.of("experimentId")))
                    && !dependencyVersionIds.equals(current.getDependencyVersionIds())) {
                return variables.update(code, current.getVersionNumber(), name, VariableValueType.DECIMAL,
                        "DEMO_MARKETING", sourceUrl, 1000, parameters, resultPath, dependencyVersionIds);
            }
            return current;
        }
        catch (NoSuchElementException missing) {
            return variables.create(code, name, VariableValueType.DECIMAL, "DEMO_MARKETING",
                    sourceUrl, 1000, parameters, resultPath, dependencyVersionIds,
                    MarketingDemoFlows.BUDGET_VARIABLE.equals(code) ? "BUDGET" : "TRANSACTION");
        }
    }

    private RuleDefinition sampleRules(VariableVersion order, VariableVersion margin, VariableVersion budget) {
        RuleDefinition definition = json.readValue("""
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
        definition.getInputs().get(0).setVariableBinding(binding(order));
        definition.getInputs().get(1).setVariableBinding(binding(margin));
        definition.getInputs().get(2).setVariableBinding(binding(budget));
        return definition;
    }

    private RuleVariableBinding binding(VariableVersion version) {
        RuleVariableBinding binding = new RuleVariableBinding();
        binding.setVariableCode(version.getVariableCode());
        binding.setVariableVersionId(version.getId());
        return binding;
    }

    private boolean matchesBinding(com.dmp.riskdecision.rule.model.RuleInput input, VariableVersion version) {
        RuleVariableBinding binding = input.getVariableBinding();
        return binding != null && version.getVariableCode().equals(binding.getVariableCode())
                && binding.getVariableVersionId() != null
                && version.getId() == binding.getVariableVersionId();
    }
}
