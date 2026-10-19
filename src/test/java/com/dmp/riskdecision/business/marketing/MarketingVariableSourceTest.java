package com.dmp.riskdecision.business.marketing;

import com.dmp.riskdecision.business.marketing.repository.MarketingDemoStore;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableService;
import com.dmp.riskdecision.business.marketing.service.MarketingDemoVariableSource;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证营销样板事实经变量中心不可变版本取值。 */
@SpringBootTest
class MarketingVariableSourceTest {
    @Resource private VariableService variables;
    @Resource private MarketingDemoStore store;
    @Resource private MarketingDemoVariableSource demoSource;

    @Test
    void resolvesCheckoutAndBudgetByBusinessIdentifiers() {
        String suffix = Long.toString(System.nanoTime());
        String experiment = "experiment" + suffix;
        String uid = "user" + suffix;
        store.stageCheckout(experiment, "checkoutA" + suffix, uid,
                new BigDecimal("120"), new BigDecimal("30"), BigDecimal.ZERO);
        store.stageCheckout(experiment, "checkoutB" + suffix, uid,
                new BigDecimal("80"), new BigDecimal("25"), BigDecimal.ZERO);

        VariableVersion amount = currentOrCreate(MarketingDemoFlows.ORDER_AMOUNT_VARIABLE,
                "订单金额", "marketing://checkout", List.of("uid", "checkoutId", "experimentId"), "orderAmount");
        VariableVersion margin = currentOrCreate(MarketingDemoFlows.MARGIN_VARIABLE,
                "预计毛利", "marketing://checkout", List.of("uid", "checkoutId", "experimentId"), "marginAfterMaxOffer");
        VariableVersion budget = currentOrCreate(MarketingDemoFlows.BUDGET_VARIABLE,
                "剩余预算", "marketing://budget", List.of("uid", "checkoutId", "experimentId"), "remainingBudget");

        Map<String, Object> first = Map.of("uid", uid, "checkoutId", "checkoutA" + suffix,
                "experimentId", experiment);
        Map<String, Object> second = Map.of("uid", uid, "checkoutId", "checkoutB" + suffix,
                "experimentId", experiment);
        assertThat((BigDecimal) variables.resolve(amount.getId(), amount.getVariableCode(), first))
                .isEqualByComparingTo("120");
        assertThat((BigDecimal) variables.resolve(amount.getId(), amount.getVariableCode(), second))
                .isEqualByComparingTo("80");
        assertThat((BigDecimal) variables.resolve(margin.getId(), margin.getVariableCode(), first))
                .isEqualByComparingTo("12");
        assertThat((BigDecimal) variables.resolve(budget.getId(), budget.getVariableCode(),
                first)).isEqualByComparingTo("2000");
        assertThatThrownBy(() -> variables.resolve(amount.getId(), amount.getVariableCode(),
                Map.of("uid", "anotherUser", "checkoutId", "checkoutA" + suffix,
                        "experimentId", experiment))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> variables.create("arbitrary" + suffix, "任意字段", VariableValueType.DECIMAL,
                "DEMO_MARKETING", "marketing://checkout", 1000,
                List.of("uid", "checkoutId", "experimentId"), "orderAmount"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> variables.update(MarketingDemoFlows.ORDER_AMOUNT_VARIABLE,
                amount.getVersionNumber(), "错误映射", VariableValueType.DECIMAL,
                "DEMO_MARKETING", "marketing://checkout", 1000,
                List.of("uid", "checkoutId", "experimentId"), "marginAfterMaxOffer"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private VariableVersion currentOrCreate(String code, String name, String url,
                                            List<String> parameters, String path) {
        try { return variables.current(code); }
        catch (java.util.NoSuchElementException missing) {
            return variables.create(code, name, VariableValueType.DECIMAL,
                    "DEMO_MARKETING", url, 1000, parameters, path);
        }
    }

    @Test
    void oldPublishedBudgetVersionRemainsExecutable() {
        String experiment = "legacyBudget" + System.nanoTime();
        store.stageCheckout(experiment, "checkoutOld", "userOld",
                new BigDecimal("120"), new BigDecimal("30"), BigDecimal.ZERO);
        VariableVersion legacy = new VariableVersion(99, MarketingDemoFlows.BUDGET_VARIABLE,
                "旧预算", 1, VariableValueType.DECIMAL, "DEMO_MARKETING",
                "marketing://budget", 1000, List.of("experimentId"), "remainingBudget");
        assertThat((BigDecimal) demoSource.fetch(legacy, Map.of("experimentId", experiment)))
                .isEqualByComparingTo("2000");
    }
}
