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
import com.dmp.riskdecision.orchestrator.graph.FlowContextBinding;
import com.dmp.riskdecision.orchestrator.graph.GraphIssue;
import com.dmp.riskdecision.orchestrator.graph.GraphNode;
import com.dmp.riskdecision.orchestrator.graph.GraphSourceAvailability;
import com.dmp.riskdecision.orchestrator.graph.NodeKind;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Deque;
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
        requireValidContextBindings(graph);
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

    /** 每个流程变量只能有一个生产者，发布图固定变量版本和来源节点。 */
    private void requireValidContextBindings(GraphDefinition graph) {
        Set<String> names = new HashSet<>();
        for (FlowContextBinding binding : graph.getContextBindings()) {
            if (binding == null || binding.getVariableCode() == null
                    || !names.add(binding.getVariableCode()) || binding.getVariableVersionId() <= 0
                    || binding.getNodeId() == null || binding.getResultPath() == null
                    || !binding.getResultPath().isEmpty() && !binding.getResultPath().matches(
                    "[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*){0,10}")) {
                throw new GraphCompilationException(List.of(new GraphIssue("VARIABLE_CONFIG_INVALID", null,
                        null, "Context variable binding is invalid or duplicated")));
            }
            VariableVersion variable;
            try { variable = variables.current(binding.getVariableCode()); }
            catch (NoSuchElementException missing) {
                throw new GraphCompilationException(List.of(new GraphIssue("VARIABLE_CONFIG_INVALID",
                        binding.getNodeId(), null, "Context variable is unavailable")));
            }
            if (variable.getId() != binding.getVariableVersionId()
                    || !"FLOW_CONTEXT".equals(variable.getSourceType())
                    || graph.getNodes().stream().noneMatch(node -> binding.getNodeId().equals(node.getId()))) {
                throw new GraphCompilationException(List.of(new GraphIssue("VARIABLE_CONFIG_INVALID",
                        binding.getNodeId(), null, "Context variable version or producer is invalid")));
            }
            GraphNode producer = graph.getNodes().stream()
                    .filter(node -> binding.getNodeId().equals(node.getId())).findFirst().orElseThrow();
            requireStaticProducerOutput(binding, producer, variable);
        }
    }

    /** 已知组件的输出结构在发布时检查，动态业务明细仍由运行时检查。 */
    private void requireStaticProducerOutput(FlowContextBinding binding, GraphNode producer,
                                             VariableVersion variable) {
        String path = binding.getResultPath();
        Object value = null;
        if (producer.getKind() == NodeKind.CONDITION) {
            throw invalidVariable(producer, "Condition nodes do not store a reusable node result");
        }
        if (producer.getKind() == NodeKind.SUBFLOW) {
            requireSubflowResultPath(binding, producer, variable);
            return;
        }
        if (producer.getKind() != NodeKind.COMPONENT) {
            throw invalidVariable(producer, "Context producer does not output a node result");
        }
        if ("emit-value".equals(producer.getComponentKey())) {
            value = producer.getConfig() == null ? null : producer.getConfig().get("value");
            if (!path.isEmpty()) {
                for (String part : path.split("\\.")) {
                    if (!(value instanceof Map<?, ?>)) {
                        throw invalidVariable(producer, "Context result path is unavailable: " + path);
                    }
                    value = ((Map<?, ?>) value).get(part);
                }
            }
        } else if ("get-variable".equals(producer.getComponentKey())) {
            Object pinned = producer.getConfig() == null ? null : producer.getConfig().get("versionId");
            VariableVersion source;
            try { source = pinned instanceof Number
                    ? variables.requireVersion(((Number) pinned).longValue()) : null; }
            catch (NoSuchElementException missing) { source = null; }
            if (source == null || !path.isEmpty()
                    || source.getValueType() != variable.getValueType()) {
                throw invalidVariable(producer, "Context variable source type or path is invalid");
            }
            return;
        } else if ("execute-rule-set".equals(producer.getComponentKey())) {
            VariableValueType type = "ruleVersionId".equals(path) ? VariableValueType.DECIMAL
                    : Set.of("resultCode", "reasonCode", "reason", "ruleSetCode").contains(path)
                    ? VariableValueType.STRING : null;
            if (type != variable.getValueType()) {
                throw invalidVariable(producer, "Context rule result path or type is invalid: " + path);
            }
            return;
        } else {
            return;
        }
        if (value == null || variable.getValueType() == VariableValueType.STRING && !(value instanceof String)
                || variable.getValueType() == VariableValueType.BOOLEAN && !(value instanceof Boolean)
                || variable.getValueType() == VariableValueType.DECIMAL && !(value instanceof Number)) {
            throw invalidVariable(producer, "Context result path or type is invalid: " + path);
        }
    }

    /** 子流程固定结果的外层字段可静态识别，业务 details 内部由运行时校验。 */
    private void requireSubflowResultPath(FlowContextBinding binding, GraphNode producer,
                                          VariableVersion variable) {
        String path = binding.getResultPath();
        if (Set.of("flowCode", "status").contains(path)
                && variable.getValueType() == VariableValueType.STRING
                || "versionId".equals(path) && variable.getValueType() == VariableValueType.DECIMAL) return;
        if (path.startsWith("businessResult.")) {
            String nested = path.substring("businessResult.".length());
            if (Set.of("conclusion", "reasonCode", "reason").contains(nested)
                    && variable.getValueType() == VariableValueType.STRING
                    || nested.startsWith("details.") || nested.startsWith("suggestedAction.")) return;
        }
        throw invalidVariable(producer, "Context subflow result path or type is invalid: " + path);
    }

    /** 校验固定变量版本和当前流程声明，发布时不取变量值。 */
    private void requireValidVariableNode(GraphDefinition graph, GraphNode node, Map<String, Object> config) {
        boolean shape = node.getKind() == NodeKind.COMPONENT && config != null
                && config.keySet().equals(Set.of("variableCode", "versionId"))
                && config.get("variableCode") instanceof String
                && config.get("versionId") instanceof Number;
        if (!shape) throw invalidVariable(node, "Variable node configuration is invalid");
        String code = (String) config.get("variableCode");
        long versionId = ((Number) config.get("versionId")).longValue();
        VariableVersion current;
        try { current = variables.current(code); }
        catch (NoSuchElementException missing) { throw invalidVariable(node, "Variable is unavailable"); }
        if (current.getId() != versionId) throw invalidVariable(node, "Variable version is outdated");
        requireVariableDependencies(graph, node, current, new HashSet<>());
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

    /** 规则执行节点固定当前发布版本；变量绑定由规则集管理。 */
    private void requireValidRuleNode(GraphDefinition graph, GraphNode node, Map<String, Object> config) {
        if (node.getKind() != NodeKind.COMPONENT || config == null
                || !config.keySet().equals(Set.of("ruleSetCode", "ruleVersionId"))
                || !(config.get("ruleSetCode") instanceof String)
                || !(config.get("ruleVersionId") instanceof Number)) {
            throw invalidRule(node, "Rule node configuration is invalid");
        }
        String code = (String) config.get("ruleSetCode");
        long id = ((Number) config.get("ruleVersionId")).longValue();
        RuleSetVersion version;
        try { version = rules.currentVersion(code); }
        catch (NoSuchElementException missing) { throw invalidRule(node, "Rule set is unavailable"); }
        if (version.getId() != id) throw invalidRule(node, "Rule set version is outdated");
        for (RuleInput input : version.getDefinition().getInputs()) {
            if (input.getVariableBinding() == null) throw invalidRule(node, "Rule input has no variable");
            VariableVersion variable;
            try { variable = variables.requireVersion(input.getVariableBinding().getVariableVersionId()); }
            catch (NoSuchElementException missing) { throw invalidRule(node, "Rule variable is unavailable"); }
            requireVariableDependencies(graph, node, variable, new HashSet<>());
        }
    }

    /** 外部变量的每级固定依赖最终也必须能从本流程输入或上游节点取得。 */
    private void requireVariableDependencies(GraphDefinition graph, GraphNode consumer,
                                             VariableVersion variable, Set<Long> visited) {
        if (!visited.add(variable.getId())) return;
        String code = variable.getVariableCode();
        if ("REQUEST_INPUT".equals(variable.getSourceType())) {
            if (graph.getRequiredInputs() == null || !graph.getRequiredInputs().contains(code)) {
                throw invalidVariable(consumer, "Request variable is not declared: " + code);
            }
            return;
        }
        if ("FLOW_CONTEXT".equals(variable.getSourceType())) {
            FlowContextBinding binding = graph.getContextBindings().stream()
                    .filter(item -> code.equals(item.getVariableCode())).findFirst().orElse(null);
            if (binding == null || binding.getVariableVersionId() != variable.getId()
                    || !isUpstream(graph, binding.getNodeId(), consumer.getId())) {
                throw invalidVariable(consumer, "Context variable has no upstream producer: " + code);
            }
            return;
        }
        for (String dependencyCode : variable.getParameterNames()) {
            Long id = variable.getDependencyVersionIds().get(dependencyCode);
            VariableVersion dependency;
            try { dependency = id == null ? null : variables.requireVersion(id); }
            catch (NoSuchElementException missing) { dependency = null; }
            if (dependency == null || !dependencyCode.equals(dependency.getVariableCode())) {
                throw invalidVariable(consumer, "Variable dependency is unavailable: " + dependencyCode);
            }
            requireVariableDependencies(graph, consumer, dependency, visited);
        }
    }

    /** 生产节点须能先于规则节点执行；分支未经过生产者时留给运行时缺值检查。 */
    private boolean isUpstream(GraphDefinition graph, String producerId, String consumerId) {
        if (producerId.equals(consumerId)) return false;
        Deque<String> pending = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        pending.add(producerId);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!seen.add(current)) continue;
            for (com.dmp.riskdecision.orchestrator.graph.GraphEdge edge : graph.getEdges()) {
                if (!current.equals(edge.getSource())) continue;
                if (consumerId.equals(edge.getTarget())) return true;
                pending.add(edge.getTarget());
            }
        }
        return false;
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
