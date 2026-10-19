package com.dmp.riskdecision.business.marketing.service;

import com.dmp.riskdecision.business.marketing.repository.MarketingDemoStore;
import com.dmp.riskdecision.business.marketing.MarketingDemoFlows;
import com.dmp.riskdecision.component.variable.model.VariableValueType;
import com.dmp.riskdecision.component.variable.model.VariableVersion;
import com.dmp.riskdecision.component.variable.service.VariableResolutionException;
import com.dmp.riskdecision.component.variable.service.VariableSource;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 从当前进程营销演示数据读取业务单值，供变量中心验证可配置取值路径。 */
@Service
public class MarketingDemoVariableSource implements VariableSource {
    private static final BigDecimal HIGH_OFFER_AMOUNT = new BigDecimal("18.00");
    private static final BigDecimal DEMO_BUDGET = new BigDecimal("2000.00");
    @Resource private MarketingDemoStore store;

    /** {@inheritDoc} */
    @Override
    public String sourceType() { return "DEMO_MARKETING"; }

    /** {@inheritDoc} 样板来源只允许三项固定业务定义，防止变量码与取值含义错配。 */
    @Override
    public void validateDefinition(String code, VariableValueType valueType, String url,
                                   List<String> parameters, String resultPath) {
        boolean checkoutParams = parameters != null && parameters.size() == 3
                && new HashSet<>(parameters).equals(Set.of("uid", "checkoutId", "experimentId"));
        boolean order = MarketingDemoFlows.ORDER_AMOUNT_VARIABLE.equals(code)
                && "marketing://checkout".equals(url) && "orderAmount".equals(resultPath) && checkoutParams;
        boolean margin = MarketingDemoFlows.MARGIN_VARIABLE.equals(code)
                && "marketing://checkout".equals(url) && "marginAfterMaxOffer".equals(resultPath) && checkoutParams;
        boolean budget = MarketingDemoFlows.BUDGET_VARIABLE.equals(code)
                && "marketing://budget".equals(url) && "remainingBudget".equals(resultPath)
                && checkoutParams;
        if (valueType != VariableValueType.DECIMAL || !order && !margin && !budget) {
            throw new IllegalArgumentException("Invalid marketing demo variable definition");
        }
    }

    /** {@inheritDoc} 只接受变量定义中声明的参数及已知样板字段。 */
    @Override
    public Object fetch(VariableVersion version, Map<String, Object> parameters) {
        if (parameters == null || !parameters.keySet().equals(new HashSet<>(version.getParameterNames()))) {
            throw new IllegalArgumentException("Marketing variable parameters do not match definition");
        }
        Object experiment = parameters.get("experimentId");
        if (!(experiment instanceof String) || ((String) experiment).isBlank()) {
            throw new VariableResolutionException("Marketing experiment ID is missing");
        }
        MarketingDemoStore active = MarketingDemoStore.currentForThread();
        MarketingDemoStore facts = active == null ? store : active;
        if (MarketingDemoFlows.BUDGET_VARIABLE.equals(version.getVariableCode())
                && "marketing://budget".equals(version.getUrl())
                && "remainingBudget".equals(version.getResultPath())
                && version.getParameterNames().equals(List.of("experimentId"))) {
            // 已发布的旧预算版本只用实验 ID；保留原取值语义供历史流程执行。
            return DEMO_BUDGET.subtract(facts.actualDiscountSpent((String) experiment));
        }
        Object checkoutId = parameters.get("checkoutId");
        Object uid = parameters.get("uid");
        if (!(checkoutId instanceof String)
                || !(uid instanceof String)) {
            throw new VariableResolutionException("Marketing checkout variable source is invalid");
        }
        MarketingDemoStore.CheckoutFact fact;
        try { fact = facts.requireStaged(
                (String) experiment, (String) checkoutId, (String) uid); }
        catch (java.util.NoSuchElementException missing) {
            throw new VariableResolutionException("Marketing checkout facts are unavailable", missing);
        }
        if ("marketing://budget".equals(version.getUrl())
                && "remainingBudget".equals(version.getResultPath())) {
            return DEMO_BUDGET.subtract(fact.getDiscountSpentAtDecision());
        }
        if (!"marketing://checkout".equals(version.getUrl())) {
            throw new VariableResolutionException("Marketing checkout variable source is invalid");
        }
        return switch (version.getResultPath()) {
            case "orderAmount" -> fact.getOrderAmount();
            case "marginAfterMaxOffer" -> fact.getPreDiscountMargin().subtract(HIGH_OFFER_AMOUNT);
            default -> throw new VariableResolutionException("Marketing variable field is unsupported");
        };
    }
}
