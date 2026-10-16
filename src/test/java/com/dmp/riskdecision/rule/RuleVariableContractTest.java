package com.dmp.riskdecision.rule;

import com.dmp.riskdecision.rule.model.RuleDefinition;
import com.dmp.riskdecision.rule.model.RuleInput;
import com.dmp.riskdecision.rule.service.RuleVariableBindingService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 规则输入必须由变量中心定义，不能回退到调用方直接赋值。 */
class RuleVariableContractTest {
    @Test
    void rejectsAnInputWithoutVariableBinding() {
        RuleInput input = new RuleInput();
        input.setName("amount");
        input.setType("DECIMAL");
        RuleDefinition definition = new RuleDefinition();
        definition.setInputs(List.of(input));

        assertThatThrownBy(() -> new RuleVariableBindingService().requireValid(definition, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }
}
