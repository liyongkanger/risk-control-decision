package com.dmp.riskdecision.rule.service;

import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleInput;
import com.dmp.riskdecision.rule.model.RuleVariableBinding;
import com.dmp.riskdecision.orchestrator.graph.FlowContextBinding;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** 校验规则输入的变量绑定，并按固定版本解析规则执行值。 */
@Service
public class RuleVariableBindingService {
    @Resource private VariableService variables;

    /** 保存草稿可引用历史版本；发布时要求绑定当前变量版本。 */
    public void requireValid(RuleDefinition definition, boolean publishing) {
        for (RuleInput input : definition.getInputs()) {
            RuleVariableBinding binding = input.getVariableBinding();
            if (binding == null || binding.getVariableCode() == null
                    || binding.getVariableVersionId() == null) {
                throw new IllegalArgumentException("Rule variable binding is incomplete: " + input.getName());
            }
            VariableVersion version;
            try { version = variables.requireVersion(binding.getVariableVersionId()); }
            catch (NoSuchElementException missing) {
                throw new IllegalArgumentException("Rule variable version is unavailable: " + input.getName(), missing);
            }
            if (!binding.getVariableCode().equals(version.getVariableCode())
                    || !input.getType().equals(version.getValueType().name())
                    || publishing && variables.current(binding.getVariableCode()).getId() != version.getId()) {
                throw new IllegalArgumentException("Rule variable binding is invalid: " + input.getName());
            }
        }
    }

    /** 所有规则字段按固定变量版本取值，不接受直接注入的字段值。 */
    public Map<String, Object> resolve(RuleDefinition definition, Map<String, Object> requestInput,
                                       Map<String, Object> nodeResults,
                                       Map<String, FlowContextBinding> contextBindings) {
        Map<String, Object> values = new HashMap<>();
        Map<Long, Object> cache = new HashMap<>();
        for (RuleInput input : definition.getInputs()) {
            RuleVariableBinding binding = input.getVariableBinding();
            values.put(input.getName(), variables.resolveInFlow(binding.getVariableVersionId(),
                    binding.getVariableCode(), requestInput, nodeResults, contextBindings, cache));
        }
        return values;
    }

    /** 规则独立试算使用显式临时样例，绝不把样例作为正式流程上下文。 */
    public Map<String, Object> resolveForTest(RuleDefinition definition, Map<String, Object> requestInput,
                                               Map<String, Object> contextSamples) {
        Map<String, Object> values = new HashMap<>();
        Map<Long, Object> cache = new HashMap<>();
        for (RuleInput input : definition.getInputs()) {
            RuleVariableBinding binding = input.getVariableBinding();
            values.put(input.getName(), variables.resolveForTest(binding.getVariableVersionId(),
                    binding.getVariableCode(), requestInput, contextSamples, cache));
        }
        return values;
    }
}
