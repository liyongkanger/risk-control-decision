package com.dmp.riskdecision.rule;

import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleEvaluation;
import com.dmp.riskdecision.rule.service.RuleEvaluator;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证规则表的首命中、兜底和输入错误语义。 */
class RuleEvaluatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RuleEvaluator evaluator = new RuleEvaluator();

    @Test
    void returnsFirstMatchingRowWithReason() {
        RuleEvaluation result = evaluator.evaluate(definition(), "eligibility", 7,
                Map.of("amount", new BigDecimal("80"), "margin", new BigDecimal("5")));
        assertEquals("INELIGIBLE", result.getResultCode());
        assertEquals("ORDER_LOW", result.getReasonCode());
        assertEquals("amount-low", result.getMatchedRuleId());
        assertEquals(7, result.getRuleVersionId());
    }

    @Test
    void allConditionsInRowMustMatch() {
        RuleEvaluation result = evaluator.evaluate(definition(), "eligibility", 7,
                Map.of("amount", new BigDecimal("120"), "margin", new BigDecimal("5")));
        assertEquals("MARGIN_LOW", result.getReasonCode());
        assertEquals("margin-low", result.getMatchedRuleId());
    }

    @Test
    void returnsDefaultWhenNoRowMatches() {
        RuleEvaluation result = evaluator.evaluate(definition(), "eligibility", 7,
                Map.of("amount", new BigDecimal("120"), "margin", new BigDecimal("30")));
        assertEquals("ELIGIBLE", result.getResultCode());
        assertNull(result.getMatchedRuleId());
    }

    @Test
    void missingOrWrongTypedInputFailsInsteadOfReturningDefault() {
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(definition(), "eligibility", 7,
                Map.of("amount", new BigDecimal("120"))));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(definition(), "eligibility", 7,
                Map.of("amount", "not-a-decimal", "margin", new BigDecimal("30"))));
    }

    private RuleDefinition definition() {
        return json.readValue("""
                {"inputs":[{"name":"amount","type":"DECIMAL"},{"name":"margin","type":"DECIMAL"}],
                 "resultCodes":["ELIGIBLE","INELIGIBLE"],
                 "rows":[
                   {"id":"amount-low","priority":10,"conditions":[{"input":"amount","operator":"LT","expected":100},{"input":"margin","operator":"LT","expected":10}],"result":{"resultCode":"INELIGIBLE","reasonCode":"ORDER_LOW","reason":"订单和利润不足"}},
                   {"id":"margin-low","priority":20,"conditions":[{"input":"margin","operator":"LT","expected":10}],"result":{"resultCode":"INELIGIBLE","reasonCode":"MARGIN_LOW","reason":"利润不足"}}
                 ],"defaultResult":{"resultCode":"ELIGIBLE","reasonCode":"QUALIFIED","reason":"符合资格"}}
                """, RuleDefinition.class);
    }
}
