package com.dmp.riskdecision.rule.service;

import com.dmp.riskdecision.rule.model.RuleCondition;
import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleEvaluation;
import com.dmp.riskdecision.rule.model.RuleInput;
import com.dmp.riskdecision.rule.model.RuleRow;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 对一组已固定的强类型输入执行无副作用规则表。 */
@Service
public class RuleEvaluator {
    /** 先检查所有输入，再按升序返回第一条命中规则或兜底结果。 */
    public RuleEvaluation evaluate(RuleDefinition definition, String code, long versionId,
                                   Map<String, Object> values) {
        Map<String, String> types = new java.util.HashMap<>();
        for (RuleInput input : definition.getInputs()) {
            types.put(input.getName(), input.getType());
            Object value = values == null ? null : values.get(input.getName());
            if (!correctType(input.getType(), value)) {
                throw new IllegalArgumentException("Invalid rule input: " + input.getName());
            }
        }
        for (RuleRow row : definition.getRows().stream()
                .sorted(Comparator.comparing(RuleRow::getPriority)).toList()) {
            if (row.getConditions().stream().allMatch(condition -> matches(condition, values, types))) {
                return new RuleEvaluation(row.getResult(), row.getId(), code, versionId);
            }
        }
        return new RuleEvaluation(definition.getDefaultResult(), null, code, versionId);
    }

    private boolean correctType(String type, Object value) {
        return switch (type) {
            case "STRING" -> value instanceof String;
            case "BOOLEAN" -> value instanceof Boolean;
            case "DECIMAL" -> decimal(value);
            default -> false;
        };
    }

    /** 类型兼容性在定义发布时验证；这里使用对应类型比较。 */
    private boolean matches(RuleCondition condition, Map<String, Object> values, Map<String, String> types) {
        Object actual = values.get(condition.getInput());
        Object expected = condition.getExpected();
        int compared;
        if ("DECIMAL".equals(types.get(condition.getInput()))) {
            compared = new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected.toString()));
        } else if (actual instanceof String && expected instanceof String) {
            compared = ((String) actual).compareTo((String) expected);
        } else if (actual instanceof Boolean && expected instanceof Boolean) {
            compared = ((Boolean) actual).compareTo((Boolean) expected);
        } else {
            throw new IllegalArgumentException("Rule comparison type mismatch: " + condition.getInput());
        }
        return switch (condition.getOperator()) {
            case "EQ" -> compared == 0;
            case "NE" -> compared != 0;
            case "GT" -> compared > 0;
            case "GTE" -> compared >= 0;
            case "LT" -> compared < 0;
            case "LTE" -> compared <= 0;
            default -> throw new IllegalArgumentException("Unsupported rule operator");
        };
    }

    private boolean decimal(Object value) {
        if (value instanceof BigDecimal || value instanceof BigInteger
                || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) return true;
        if (!(value instanceof String)) return false;
        return ((String) value).matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?");
    }
}
