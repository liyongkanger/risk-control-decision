package com.dmp.riskdecision.component.liteflow.node.variable;

import com.dmp.riskdecision.audit.NodeTrace;
import com.dmp.riskdecision.component.liteflow.context.ExecutionContext;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeBooleanComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 对上游强类型变量值执行相等或十进制大小比较。 */
@LiteflowComponent("compareValue")
public class CompareValueConditionAdapter extends NodeBooleanComponent {
    @Override
    public boolean processBoolean() {
        ExecutionContext context = getContextBean(ExecutionContext.class);
        String nodeId = getTag();
        Instant started = Instant.now();
        try {
            Map<String, Object> config = context.getNodeConfigs().get(nodeId);
            Object actual = context.getNodeResults().get(config.get("sourceNodeId"));
            Object expected = config.get("expected");
            VariableValueType type = VariableValueType.valueOf((String) config.get("valueType"));
            String operator = (String) config.get("operator");
            if (actual == null || expected == null) throw new IllegalStateException("Variable value is missing");
            int comparison;
            if (type == VariableValueType.DECIMAL && actual instanceof BigDecimal && expected instanceof Number) {
                comparison = ((BigDecimal) actual).compareTo(new BigDecimal(expected.toString()));
            } else if (type == VariableValueType.STRING && actual instanceof String && expected instanceof String) {
                comparison = ((String) actual).compareTo((String) expected);
            } else if (type == VariableValueType.BOOLEAN && actual instanceof Boolean && expected instanceof Boolean) {
                comparison = Objects.equals(actual, expected) ? 0 : 1;
            } else {
                throw new IllegalStateException("Variable comparison type mismatch");
            }
            boolean answer = switch (operator) {
                case "EQ" -> comparison == 0;
                case "NE" -> comparison != 0;
                case "GT" -> requireDecimal(type, comparison > 0);
                case "GTE" -> requireDecimal(type, comparison >= 0);
                case "LT" -> requireDecimal(type, comparison < 0);
                case "LTE" -> requireDecimal(type, comparison <= 0);
                default -> throw new IllegalArgumentException("Unknown variable comparison operator");
            };
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "SUCCEEDED", null));
            return answer;
        } catch (RuntimeException error) {
            context.getTraces().add(new NodeTrace(context.getRequestId(), context.getSceneCode(),
                    context.getVersion(), nodeId, started, Instant.now(), "FAILED", "VARIABLE_COMPARISON_FAILED"));
            throw error;
        }
    }

    private static boolean requireDecimal(VariableValueType type, boolean answer) {
        if (type != VariableValueType.DECIMAL) throw new IllegalStateException("Only DECIMAL supports ordering");
        return answer;
    }
}
