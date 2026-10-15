package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.orchestrator.graph.FailurePolicy;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphEdge;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import java.util.List;
import java.util.Map;

/** 结算优惠演示使用的可发布流程图定义，不承载生产业务规则。 */
public class MarketingDemoFlows {
    public static final String ELIGIBILITY_CODE = "demoOfferEligibility";
    public static final String SELECTION_CODE = "demoOfferSelection";
    public static final String OUTER_CODE = "demoCheckoutDecision";
    public static final String SCENE_CODE = "demoCheckoutScene";
    public static final String LOW_OFFER = "LOW_5_OFF_100";
    public static final String HIGH_OFFER = "HIGH_18_OFF_100";
    public static final String ELIGIBILITY_RULE_SET_CODE = "demoOfferEligibilityRules";

    /** 资格子流程把统一的资格判断表达为可追溯结论。 */
    public GraphDefinition eligibilityGraph(long ruleVersionId) {
        return graph(List.of(start(), ruleSet("eligibilityRules", ruleVersionId),
                        ruleResult("qualified", "eligibilityRules", "ELIGIBLE"),
                        eligibilityResult("eligibleResult", true),
                        eligibilityResult("ineligibleResult", false),
                        join("eligibilityJoin"), end()),
                List.of(edge("a", "start", "eligibilityRules"),
                        edge("b", "eligibilityRules", "qualified"),
                        edge("c", "qualified", "eligibleResult", "true"),
                        edge("d", "qualified", "ineligibleResult", "false"),
                        edge("e", "eligibleResult", "eligibilityJoin"),
                        edge("f", "ineligibleResult", "eligibilityJoin"),
                        edge("g", "eligibilityJoin", "end")),
                List.of("orderAmount", "marginAfterMaxOffer", "remainingBudget"));
    }

    /** 优惠选择子流程按资格、实验组和高低券组返回三种正常结论。 */
    public GraphDefinition selectionGraph() {
        return graph(List.of(start(), positive("eligibleCheck", "eligible"),
                        positive("experimentCheck", "inExperiment"),
                        positive("highCheck", "highGroup"),
                        offerResult("highResult", "HIGH_VALUE_OFFER", HIGH_OFFER),
                        offerResult("lowResult", "LOW_VALUE_OFFER", LOW_OFFER),
                        join("highJoin"),
                        offerResult("controlResult", "NO_NEW_OFFER", null),
                        join("experimentJoin"),
                        offerResult("ineligibleResult", "NO_NEW_OFFER", null),
                        join("eligibilityJoin"), end()),
                List.of(edge("a", "start", "eligibleCheck"),
                        edge("b", "eligibleCheck", "experimentCheck", "true"),
                        edge("c", "eligibleCheck", "ineligibleResult", "false"),
                        edge("d", "experimentCheck", "highCheck", "true"),
                        edge("e", "experimentCheck", "controlResult", "false"),
                        edge("f", "highCheck", "highResult", "true"),
                        edge("g", "highCheck", "lowResult", "false"),
                        edge("h", "highResult", "highJoin"),
                        edge("i", "lowResult", "highJoin"),
                        edge("j", "highJoin", "experimentJoin"),
                        edge("k", "controlResult", "experimentJoin"),
                        edge("l", "experimentJoin", "eligibilityJoin"),
                        edge("m", "ineligibleResult", "eligibilityJoin"),
                        edge("n", "eligibilityJoin", "end")),
                List.of("eligible", "inExperiment", "highGroup"));
    }

    /** 外层固定引用两个子流程版本，并独立决定最终业务结论。 */
    public GraphDefinition outerGraph(long eligibilityVersionId, long selectionVersionId) {
        GraphNode eligibilityCall = call("eligibilityCall", ELIGIBILITY_CODE, eligibilityVersionId,
                Map.of("orderAmount", requestInput("orderAmount"),
                        "marginAfterMaxOffer", requestInput("marginAfterMaxOffer"),
                        "remainingBudget", requestInput("remainingBudget")));
        GraphNode selectionCall = call("selectionCall", SELECTION_CODE, selectionVersionId,
                Map.of("eligible", nodeResult("eligibilityCall", "businessResult.details.eligible"),
                        "inExperiment", requestInput("inExperiment"),
                        "highGroup", requestInput("highGroup")));
        return graph(List.of(start(), eligibilityCall, selectionCall,
                        equalsResult("highCheck", "HIGH_VALUE_OFFER"),
                        offerResult("highResult", "HIGH_VALUE_OFFER", HIGH_OFFER),
                        equalsResult("lowCheck", "LOW_VALUE_OFFER"),
                        offerResult("lowResult", "LOW_VALUE_OFFER", LOW_OFFER),
                        offerResult("noResult", "NO_NEW_OFFER", null),
                        join("lowJoin"), join("highJoin"), end()),
                List.of(edge("a", "start", "eligibilityCall"),
                        edge("b", "eligibilityCall", "selectionCall"),
                        edge("c", "selectionCall", "highCheck"),
                        edge("d", "highCheck", "highResult", "true"),
                        edge("e", "highCheck", "lowCheck", "false"),
                        edge("f", "lowCheck", "lowResult", "true"),
                        edge("g", "lowCheck", "noResult", "false"),
                        edge("h", "lowResult", "lowJoin"),
                        edge("i", "noResult", "lowJoin"),
                        edge("j", "lowJoin", "highJoin"),
                        edge("k", "highResult", "highJoin"),
                        edge("l", "highJoin", "end")),
                List.of("orderAmount", "marginAfterMaxOffer", "remainingBudget", "inExperiment", "highGroup"));
    }

    private static GraphDefinition graph(List<GraphNode> nodes, List<GraphEdge> edges,
                                         List<String> requiredInputs) {
        GraphDefinition graph = new GraphDefinition(1, nodes, edges, Map.of());
        graph.setRequiredInputs(requiredInputs);
        return graph;
    }

    private static GraphNode start() {
        return new GraphNode("start", NodeKind.START, null, null, Map.of());
    }

    private static GraphNode end() {
        return new GraphNode("end", NodeKind.END, null, null, Map.of());
    }

    private static GraphNode join(String id) {
        return new GraphNode(id, NodeKind.CONDITION_JOIN, null, null, Map.of());
    }

    private static GraphNode positive(String id, String inputKey) {
        return new GraphNode(id, NodeKind.CONDITION, "is-positive", FailurePolicy.FAIL_FAST,
                Map.of("inputKey", inputKey));
    }

    private static GraphNode ruleSet(String id, long versionId) {
        return new GraphNode(id, NodeKind.COMPONENT, "execute-rule-set", FailurePolicy.FAIL_FAST,
                Map.of("ruleSetCode", ELIGIBILITY_RULE_SET_CODE, "ruleVersionId", versionId,
                        "inputMappings", Map.of(
                                "orderAmount", ruleRequestInput("orderAmount"),
                                "marginAfterMaxOffer", ruleRequestInput("marginAfterMaxOffer"),
                                "remainingBudget", ruleRequestInput("remainingBudget"))));
    }

    private static GraphNode ruleResult(String id, String sourceNodeId, String resultCode) {
        return new GraphNode(id, NodeKind.CONDITION, "rule-result-equals", FailurePolicy.FAIL_FAST,
                Map.of("sourceNodeId", sourceNodeId, "expectedResultCode", resultCode));
    }

    private static Map<String, Object> ruleRequestInput(String key) {
        return Map.of("source", "INPUT", "key", key);
    }

    private static GraphNode equalsResult(String id, String expected) {
        return new GraphNode(id, NodeKind.CONDITION, "equals-value", FailurePolicy.FAIL_FAST,
                Map.of("sourceNodeId", "selectionCall", "path", "businessResult.conclusion",
                        "expected", expected));
    }

    private static GraphNode eligibilityResult(String id, boolean eligible) {
        return new GraphNode(id, NodeKind.RESULT, null, null,
                Map.of("conclusion", eligible ? "ELIGIBLE" : "INELIGIBLE",
                        "reasonCode", eligible ? "QUALIFIED" : "NOT_QUALIFIED",
                        "reason", eligible ? "满足演示资格条件" : "未满足演示资格条件",
                        "details", Map.of("eligible", eligible),
                        "reasonFromRuleNodeId", "eligibilityRules"));
    }

    private static GraphNode offerResult(String id, String conclusion, String offerCode) {
        Map<String, Object> config = offerCode == null
                ? Map.of("conclusion", conclusion, "reasonCode", "NO_NEW_OFFER",
                        "reason", "本次不新增优惠", "details", Map.of())
                : Map.of("conclusion", conclusion, "reasonCode", "EXPERIMENT_GROUP",
                        "reason", "演示实验组建议优惠", "details", Map.of("offerCode", offerCode),
                        "suggestedAction", Map.of("code", "APPLY_OFFER",
                                "parameters", Map.of("offerCode", offerCode)));
        return new GraphNode(id, NodeKind.RESULT, null, null, config);
    }

    private static GraphNode call(String id, String flowCode, long versionId,
                                  Map<String, Object> inputMappings) {
        return new GraphNode(id, NodeKind.SUBFLOW, null, FailurePolicy.FAIL_FAST,
                Map.of("flowCode", flowCode, "versionId", versionId, "inputMappings", inputMappings));
    }

    private static Map<String, Object> requestInput(String key) {
        return Map.of("source", "REQUEST_INPUT", "key", key);
    }

    private static Map<String, Object> nodeResult(String nodeId, String path) {
        return Map.of("source", "NODE_RESULT", "nodeId", nodeId, "path", path);
    }

    private static GraphEdge edge(String id, String source, String target) {
        return edge(id, source, target, null);
    }

    private static GraphEdge edge(String id, String source, String target, String branch) {
        return new GraphEdge(id, source, target, branch);
    }
}
