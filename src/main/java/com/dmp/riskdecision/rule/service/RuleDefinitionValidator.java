package com.dmp.riskdecision.rule.service;

import com.dmp.riskdecision.rule.model.RuleCondition;
import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleInput;
import com.dmp.riskdecision.rule.model.RuleOutcome;
import com.dmp.riskdecision.rule.model.RuleRow;
import java.util.HashMap;
import java.util.HashSet;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 在保存和发布前检查规则表的结构、强类型比较和结果契约。 */
@Service
public class RuleDefinitionValidator {
    /** 拒绝无法确定性执行或缺少兜底结果的规则表。 */
    public void requireValid(RuleDefinition definition) {
        if (definition == null || definition.getInputs() == null || definition.getInputs().isEmpty()
                || definition.getInputs().size() > 20 || definition.getResultCodes() == null
                || definition.getResultCodes().isEmpty() || definition.getResultCodes().size() > 30
                || definition.getRows() == null || definition.getRows().size() > 200
                || definition.getDefaultResult() == null) invalid();
        Map<String, String> types = new HashMap<>();
        for (RuleInput input : definition.getInputs()) {
            if (input == null || !code(input.getName()) || input.getType() == null
                    || !Set.of("STRING", "BOOLEAN", "DECIMAL")
                    .contains(input.getType()) || types.putIfAbsent(input.getName(), input.getType()) != null) invalid();
        }
        Set<String> results = new HashSet<>();
        for (String result : definition.getResultCodes()) {
            if (!code(result) || !results.add(result)) invalid();
        }
        outcome(definition.getDefaultResult(), results);
        Set<String> ids = new HashSet<>();
        Set<Integer> priorities = new HashSet<>();
        for (RuleRow row : definition.getRows()) {
            if (row == null || !code(row.getId()) || !ids.add(row.getId())
                    || row.getPriority() == null || row.getPriority() < 0
                    || !priorities.add(row.getPriority()) || row.getConditions() == null
                    || row.getConditions().isEmpty() || row.getConditions().size() > 10) invalid();
            outcome(row.getResult(), results);
            for (RuleCondition condition : row.getConditions()) {
                if (condition == null || !types.containsKey(condition.getInput())
                        || condition.getOperator() == null
                        || !Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE").contains(condition.getOperator())) invalid();
                String type = types.get(condition.getInput());
                if (!"DECIMAL".equals(type) && !Set.of("EQ", "NE").contains(condition.getOperator())) invalid();
                Object expected = condition.getExpected();
                if ("STRING".equals(type) && !(expected instanceof String)
                        || "BOOLEAN".equals(type) && !(expected instanceof Boolean)
                        || "DECIMAL".equals(type) && !decimal(expected)) invalid();
            }
        }
    }

    private void outcome(RuleOutcome outcome, Set<String> results) {
        if (outcome == null || !results.contains(outcome.getResultCode())
                || !code(outcome.getReasonCode()) || outcome.getReason() == null
                || outcome.getReason().isBlank() || outcome.getReason().length() > 256) invalid();
    }

    private boolean code(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9_-]{0,63}");
    }

    private boolean decimal(Object value) {
        if (value instanceof BigDecimal || value instanceof BigInteger
                || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) return true;
        if (!(value instanceof String) || !((String) value).matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) return false;
        try { new BigDecimal((String) value); return true; }
        catch (NumberFormatException error) { return false; }
    }

    private void invalid() { throw new IllegalArgumentException("Invalid rule definition"); }
}
