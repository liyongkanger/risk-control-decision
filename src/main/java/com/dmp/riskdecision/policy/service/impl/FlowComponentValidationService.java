package com.dmp.riskdecision.policy.service.impl;

import com.dmp.riskdecision.component.liteflow.service.ComponentCatalogService;
import com.dmp.riskdecision.component.liteflow.service.ComponentDefinition;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.rule.model.RuleInput;
import com.dmp.riskdecision.rule.model.RuleSetVersion;
import com.dmp.riskdecision.rule.service.RuleSetService;
import com.dmp.riskdecision.orchestrator.graph.GraphCompilationException;
import com.dmp.riskdecision.orchestrator.graph.GraphDefinition;
import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.GraphSourceAvailability;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 检查流程节点引用的组件类型和配置。 */
@Service
public class FlowComponentValidationService {
    @Resource
    private ComponentCatalogService components;
    @Resource
    private VariableService variables;
    @Resource
    private RuleSetService rules;

    public FlowComponentValidationService() {
    }

    public FlowComponentValidationService(ComponentCatalogService components) {
        this.components = components;
    }

    public void requireValid(GraphDefinition graph) {
        for (GraphNode node : graph.getNodes()) {
            if (node.getKind() != NodeKind.COMPONENT && node.getKind() != NodeKind.CONDITION) continue;
            ComponentDefinition definition;
            try {
                definition = components.require(node.getComponentKey());
            } catch (IllegalArgumentException error) {
                throw new GraphCompilationException(List.of(new GraphIssue("COMPONENT_UNKNOWN", node.getId(), null,
                        "Component is not registered")));
            }
            if (definition.isCondition() != (node.getKind() == NodeKind.CONDITION)) {
                throw new GraphCompilationException(List.of(new GraphIssue("COMPONENT_KIND_MISMATCH", node.getId(), null,
                        "Component kind does not match node kind")));
            }
            Map<String, Object> config = node.getConfig();
            if (!definition.acceptsConfig(config)) {
                throw new GraphCompilationException(List.of(new GraphIssue("COMPONENT_CONFIG_INVALID",
                        node.getId(), null, "Component configuration does not match its fields")));
            }
            if ("get-variable".equals(node.getComponentKey())) {
                requireValidVariableNode(graph, node, config);
                continue;
            }
            if ("execute-rule-set".equals(node.getComponentKey())) {
                requireValidRuleNode(graph, node, config);
                continue;
            }
            if ("rule-result-equals".equals(node.getComponentKey())) {
                requireValidRuleResult(graph, node, config);
                continue;
            }
            if ("compare-value".equals(node.getComponentKey())) {
                requireValidComparison(graph, node, config);
                continue;
            }
            boolean invalidCondition = config != null && node.getKind() == NodeKind.CONDITION
                    && ("equals-value".equals(node.getComponentKey())
                    ? !(config.get("sourceNodeId") instanceof String)
                        || !(config.get("path") instanceof String)
                        || !((String) config.get("path")).matches("[A-Za-z][A-Za-z0-9_.]{0,127}")
                        || !(config.get("expected") instanceof String)
                    : !(config.get("inputKey") instanceof String));
            if (config == null || (node.getKind() == NodeKind.COMPONENT && config.get("value") == null)
                    || invalidCondition) {
                throw new GraphCompilationException(List.of(new GraphIssue("COMPONENT_CONFIG_INVALID", node.getId(), null,
                        "Component configuration is invalid")));
            }
            if (node.getKind() == NodeKind.CONDITION && "equals-value".equals(node.getComponentKey())
                    && !GraphSourceAvailability.isAvailable(graph, (String) config.get("sourceNodeId"), node.getId())) {
                throw new GraphCompilationException(List.of(new GraphIssue("CONDITION_SOURCE_UNAVAILABLE",
                        node.getId(), null, "Condition source must execute on every path before this node")));
            }
        }
    }

    /** 校验固定变量版本、参数映射和请求输入声明，发布时不取变量值。 */
    private void requireValidVariableNode(GraphDefinition graph, GraphNode node, Map<String, Object> config) {
        boolean shape = node.getKind() == NodeKind.COMPONENT && config != null
                && config.keySet().equals(Set.of("variableCode", "versionId", "inputMappings"))
                && config.get("variableCode") instanceof String
                && config.get("versionId") instanceof Number
                && config.get("inputMappings") instanceof Map;
        if (!shape) throw invalidVariable(node, "Variable node configuration is invalid");
        String code = (String) config.get("variableCode");
        long versionId = ((Number) config.get("versionId")).longValue();
        VariableVersion current;
        try { current = variables.current(code); }
        catch (NoSuchElementException missing) { throw invalidVariable(node, "Variable is unavailable"); }
        if (current.getId() != versionId) throw invalidVariable(node, "Variable version is outdated");
        Map<?, ?> mappings = (Map<?, ?>) config.get("inputMappings");
        if (!mappings.keySet().equals(new HashSet<>(current.getParameterNames()))) {
            throw invalidVariable(node, "Variable parameter mappings are incomplete");
        }
        for (Object input : mappings.values()) {
            if (!(input instanceof String) || graph.getRequiredInputs() == null
                    || !graph.getRequiredInputs().contains(input)) {
                throw invalidVariable(node, "Variable input must be declared by the flow");
            }
        }
    }

    /** 比较节点必须引用上游获取变量节点，且预期值类型与该变量一致。 */
    private void requireValidComparison(GraphDefinition graph, GraphNode node, Map<String, Object> config) {
        if (node.getKind() != NodeKind.CONDITION || !config.keySet().equals(
                Set.of("sourceNodeId", "operator", "valueType", "expected"))) {
            throw invalidVariable(node, "Variable comparison configuration is invalid");
        }
        String sourceId = (String) config.get("sourceNodeId");
        GraphNode source = graph.getNodes().stream().filter(item -> item.getId().equals(sourceId))
                .findFirst().orElse(null);
        if (source == null || !"get-variable".equals(source.getComponentKey())
                || !GraphSourceAvailability.isAvailable(graph, sourceId, node.getId())) {
            throw invalidVariable(node, "Comparison source must be an available variable node");
        }
        Object pinnedId = source.getConfig() == null ? null : source.getConfig().get("versionId");
        if (!(pinnedId instanceof Number)) {
            throw invalidVariable(node, "Comparison source has no fixed variable version");
        }
        VariableVersion version;
        try { version = variables.requireVersion(((Number) pinnedId).longValue()); }
        catch (NoSuchElementException missing) {
            throw invalidVariable(node, "Comparison source variable version is unavailable");
        }
        VariableValueType type = version.getValueType();
        if (!type.name().equals(config.get("valueType"))) {
            throw invalidVariable(node, "Comparison type does not match variable type");
        }
        String operator = (String) config.get("operator");
        if (!Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE").contains(operator)
                || type != VariableValueType.DECIMAL && !Set.of("EQ", "NE").contains(operator)) {
            throw invalidVariable(node, "Comparison operator is incompatible with variable type");
        }
        Object expected = config.get("expected");
        if (type == VariableValueType.STRING && !(expected instanceof String)
                || type == VariableValueType.BOOLEAN && !(expected instanceof Boolean)
                || type == VariableValueType.DECIMAL && !(expected instanceof Number)) {
            throw invalidVariable(node, "Comparison expected value has the wrong type");
        }
    }

    private static GraphCompilationException invalidVariable(GraphNode node, String message) {
        return new GraphCompilationException(List.of(new GraphIssue("VARIABLE_CONFIG_INVALID", node.getId(), null, message)));
    }

    /** 规则执行节点固定当前发布版本，并映射全部声明输入。 */
    private void requireValidRuleNode(GraphDefinition graph, GraphNode node, Map<String, Object> config) {
        if (node.getKind() != NodeKind.COMPONENT || !config.keySet().equals(
                Set.of("ruleSetCode", "ruleVersionId", "inputMappings"))) {
            throw invalidRule(node, "Rule node configuration is invalid");
        }
        String code = (String) config.get("ruleSetCode");
        long id = ((Number) config.get("ruleVersionId")).longValue();
        RuleSetVersion version;
        try { version = rules.currentVersion(code); }
        catch (NoSuchElementException missing) { throw invalidRule(node, "Rule set is unavailable"); }
        if (version.getId() != id) throw invalidRule(node, "Rule set version is outdated");
        Map<?, ?> mappings = (Map<?, ?>) config.get("inputMappings");
        Set<String> names = new HashSet<>();
        for (RuleInput input : version.getDefinition().getInputs()) names.add(input.getName());
        if (!mappings.keySet().equals(names)) throw invalidRule(node, "Rule input mappings are incomplete");
        for (RuleInput input : version.getDefinition().getInputs()) {
            Object raw = mappings.get(input.getName());
            if (!(raw instanceof Map)) throw invalidRule(node, "Rule input mapping is invalid");
            Map<?, ?> source = (Map<?, ?>) raw;
            if ("INPUT".equals(source.get("source")) && source.keySet().equals(Set.of("source", "key"))) {
                if (!(source.get("key") instanceof String) || graph.getRequiredInputs() == null
                        || !graph.getRequiredInputs().contains(source.get("key"))) {
                    throw invalidRule(node, "Rule input must be declared by the flow");
                }
                continue;
            }
            if ("NODE_RESULT".equals(source.get("source")) && source.keySet().equals(Set.of("source", "nodeId"))) {
                Object sourceId = source.get("nodeId");
                GraphNode upstream = graph.getNodes().stream()
                        .filter(candidate -> candidate.getId().equals(sourceId)).findFirst().orElse(null);
                if (upstream == null || !"get-variable".equals(upstream.getComponentKey())
                        || !GraphSourceAvailability.isAvailable(graph, (String) sourceId, node.getId())) {
                    throw invalidRule(node, "Rule source variable must run on every path before this node");
                }
                Object fixedId = upstream.getConfig().get("versionId");
                if (!(fixedId instanceof Number)) throw invalidRule(node, "Rule source variable has no version");
                VariableVersion variable;
                try { variable = variables.requireVersion(((Number) fixedId).longValue()); }
                catch (NoSuchElementException missing) { throw invalidRule(node, "Rule source variable is unavailable"); }
                if (!variable.getValueType().name().equals(input.getType())) {
                    throw invalidRule(node, "Rule input type does not match variable type");
                }
                continue;
            }
            throw invalidRule(node, "Rule input mapping source is invalid");
        }
    }

    /** 结果条件只能引用已执行规则节点声明过的结果码。 */
    private void requireValidRuleResult(GraphDefinition graph, GraphNode node, Map<String, Object> config) {
        if (node.getKind() != NodeKind.CONDITION || !config.keySet().equals(
                Set.of("sourceNodeId", "expectedResultCode"))) {
            throw invalidRule(node, "Rule result condition configuration is invalid");
        }
        String sourceId = (String) config.get("sourceNodeId");
        GraphNode source = graph.getNodes().stream().filter(item -> item.getId().equals(sourceId))
                .findFirst().orElse(null);
        if (source == null || !"execute-rule-set".equals(source.getComponentKey())
                || !GraphSourceAvailability.isAvailable(graph, sourceId, node.getId())) {
            throw invalidRule(node, "Rule result source must execute on every path before this condition");
        }
        Object versionId = source.getConfig().get("ruleVersionId");
        if (!(versionId instanceof Number)) throw invalidRule(node, "Rule result source has no version");
        RuleSetVersion version;
        try { version = rules.requireVersion(((Number) versionId).longValue()); }
        catch (NoSuchElementException missing) { throw invalidRule(node, "Rule result source is unavailable"); }
        if (!version.getDefinition().getResultCodes().contains(config.get("expectedResultCode"))) {
            throw invalidRule(node, "Rule result code is not declared");
        }
    }

    private static GraphCompilationException invalidRule(GraphNode node, String message) {
        return new GraphCompilationException(List.of(new GraphIssue("RULE_CONFIG_INVALID", node.getId(), null, message)));
    }
}
