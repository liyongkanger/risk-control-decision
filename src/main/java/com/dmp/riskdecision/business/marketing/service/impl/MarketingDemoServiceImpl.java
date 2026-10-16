package com.dmp.riskdecision.business.marketing.service.impl;

import com.dmp.riskdecision.decision.model.BusinessResult;
import com.dmp.riskdecision.decision.model.ExecutionResult;
import com.dmp.riskdecision.decision.service.DecisionExecutionService;
import com.dmp.riskdecision.business.marketing.MarketingDemoFlowService;
import com.dmp.riskdecision.business.marketing.MarketingDemoFlows;
import com.dmp.riskdecision.business.marketing.MarketingDemoMetrics;
import com.dmp.riskdecision.business.marketing.MarketingDemoSetup;
import com.dmp.riskdecision.business.marketing.model.CheckoutAttempt;
import com.dmp.riskdecision.business.marketing.model.DemoGroup;
import com.dmp.riskdecision.business.marketing.model.MarketingDemoReport;
import com.dmp.riskdecision.business.marketing.model.MarketingRuleTrace;
import com.dmp.riskdecision.business.marketing.repository.MarketingDemoStore;
import com.dmp.riskdecision.business.marketing.service.MarketingDemoService;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 当前进程内的营销样板用例实现。
 * 决策调用已发布的真实流程；订单、支付和退款仅使用内存仓储模拟。
 */
@Service
@Slf4j
public class MarketingDemoServiceImpl implements MarketingDemoService {
    private static final BigDecimal HIGH_DISCOUNT = new BigDecimal("18.00");
    private static final BigDecimal LOW_DISCOUNT = new BigDecimal("5.00");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    @Resource private MarketingDemoFlowService flows;
    @Resource private DecisionExecutionService decisions;
    @Resource private MarketingDemoStore store;
    @Resource private MarketingDemoMetrics metrics;

    /** {@inheritDoc} 初始化由流程服务处理，保持重复调用的发布版本稳定。 */
    @Override
    public MarketingDemoSetup setup() {
        return flows.setup();
    }

    /**
     * {@inheritDoc}
     * 本实例内串行检查结算 ID、执行决策与保存快照，避免并发请求重复创建模拟订单。
     */
    @Override
    public synchronized CheckoutAttempt checkout(String experimentId, String checkoutId, String userId,
                                                 BigDecimal orderAmount, BigDecimal preDiscountMargin,
                                                 BigDecimal existingOfferAmount) {
        requireCode(experimentId, "experimentId");
        requireCode(checkoutId, "checkoutId");
        requireCode(userId, "userId");
        requireAmount(orderAmount, "orderAmount");
        requireAmount(preDiscountMargin, "preDiscountMargin");
        requireAmount(existingOfferAmount, "existingOfferAmount");
        CheckoutAttempt prior = store.find(experimentId, checkoutId).orElse(null);
        if (prior != null) {
            if (!prior.sameRequest(userId, orderAmount, preDiscountMargin, existingOfferAmount)) {
                throw new IllegalStateException("Checkout ID already has different inputs");
            }
            return prior;
        }
        flows.ensureInitialized();
        return evaluateCheckout(experimentId, checkoutId, userId, orderAmount,
                preDiscountMargin, existingOfferAmount);
    }

    /**
     * 先登记本次模拟结算事实；决策流程只接收业务标识，由变量中心取资格数据。
     */
    private CheckoutAttempt evaluateCheckout(String experimentId, String checkoutId, String userId,
                                             BigDecimal orderAmount, BigDecimal margin,
                                             BigDecimal existingOfferAmount) {
        DemoGroup group = groupFor(experimentId, userId);
        store.stageCheckout(experimentId, checkoutId, userId, orderAmount, margin, existingOfferAmount);
        String requestId = experimentId + ":" + checkoutId;
        ExecutionResult result = store.withCurrentFacts(() -> decisions.evaluate(requestId,
                MarketingDemoFlows.SCENE_CODE,
                Map.of("uid", userId, "checkoutId", checkoutId, "experimentId", experimentId,
                        "inExperiment", group != DemoGroup.CONTROL,
                        "highGroup", group == DemoGroup.HIGH)));
        boolean eligible = eligibilityFrom(result);
        BusinessResult conclusion = result.getBusinessResult();
        String recommended = conclusion == null ? "NONE" : offerForConclusion(conclusion.getConclusion());
        Map<String, Long> childVersions = new HashMap<>();
        for (String nodeId : new String[]{"eligibilityCall", "selectionCall"}) {
            Object raw = result.getNodeResults().get(nodeId);
            if (raw instanceof Map && ((Map<?, ?>) raw).get("versionId") instanceof Number) {
                childVersions.put(nodeId, ((Number) ((Map<?, ?>) raw).get("versionId")).longValue());
            }
        }
        CheckoutAttempt attempt = new CheckoutAttempt(experimentId, checkoutId, userId, group,
                result.getExecutionId(), result.getRequestId(), result.getVersionId(), childVersions,
                ruleTraceFrom(result),
                result.getStatus(), conclusion == null ? null : conclusion.getConclusion(),
                conclusion == null ? null : eligible ? conclusion.getReason()
                        : eligibilityReason(result, conclusion.getReason()), recommended, eligible,
                orderAmount.setScale(2), margin.setScale(2), existingOfferAmount.setScale(2));
        log.info("[MarketingDemoServiceImpl][checkout] experimentId:{} checkoutId:{} group:{} versionId:{} status:{}",
                experimentId, checkoutId, group, result.getVersionId(), result.getStatus());
        return store.save(attempt);
    }

    /** 从资格子流程的实际结论中读取是否合格，不再预先计算布尔值。 */
    private boolean eligibilityFrom(ExecutionResult result) {
        Map<?, ?> business = eligibilityBusinessResult(result);
        if (business == null) return false;
        Object details = business.get("details");
        return details instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) details).get("eligible"));
    }

    private String eligibilityReason(ExecutionResult result, String fallback) {
        Map<?, ?> business = eligibilityBusinessResult(result);
        if (business == null) return fallback;
        Object reason = business.get("reason");
        return reason instanceof String ? (String) reason : fallback;
    }

    /** 只从实际资格子流程结果提取规则追溯信息，失败或旧版无明细时返回空。 */
    private MarketingRuleTrace ruleTraceFrom(ExecutionResult result) {
        Map<?, ?> business = eligibilityBusinessResult(result);
        if (business == null || !(business.get("details") instanceof Map)) return null;
        Map<?, ?> details = (Map<?, ?>) business.get("details");
        Object code = details.get("ruleSetCode");
        Object version = details.get("ruleVersionId");
        if (!(code instanceof String) || !(version instanceof Number)) return null;
        Object matched = details.get("matchedRuleId");
        Object reason = business.get("reasonCode");
        return new MarketingRuleTrace((String) code, ((Number) version).longValue(),
                matched instanceof String ? (String) matched : null,
                reason instanceof String ? (String) reason : null);
    }

    private Map<?, ?> eligibilityBusinessResult(ExecutionResult result) {
        Object raw = result.getNodeResults().get("eligibilityCall");
        if (!(raw instanceof Map)) return null;
        Object business = ((Map<?, ?>) raw).get("businessResult");
        return business instanceof Map ? (Map<?, ?>) business : null;
    }

    /**
     * {@inheritDoc}
     * 先核对结算事件的互斥优惠，再由仓储原子处理事件重放和状态转换。
     */
    @Override
    public CheckoutAttempt feedback(String experimentId, String checkoutId, String eventId,
                                    String eventType, Boolean paid, String appliedOfferCode) {
        requireCode(experimentId, "experimentId");
        requireCode(checkoutId, "checkoutId");
        requireCode(eventId, "eventId");
        CheckoutAttempt attempt = store.require(experimentId, checkoutId);
        BigDecimal discount = null;
        if ("SETTLED".equals(eventType)) {
            if (paid == null || appliedOfferCode == null) {
                throw new IllegalArgumentException("Settlement requires paid and appliedOfferCode");
            }
            if (!paid && !"NONE".equals(appliedOfferCode)) {
                throw new IllegalArgumentException("Unpaid checkout cannot use an offer");
            }
            discount = discountFor(attempt, appliedOfferCode);
        } else if (!"REFUNDED".equals(eventType) || paid != null || appliedOfferCode != null) {
            throw new IllegalArgumentException("Invalid feedback event");
        }
        return store.feedback(experimentId, checkoutId, eventId, eventType,
                paid, appliedOfferCode, discount);
    }

    /**
     * {@inheritDoc}
     * 依照固定用户顺序填满每组十人；支付人数按最终结论预设为对照组二人、
     * 低额券组三人和高额券组四人。串行运行避免同实例内重复生成样本。
     */
    @Override
    public synchronized MarketingDemoReport runFixture(String experimentId) {
        requireCode(experimentId, "experimentId");
        flows.ensureInitialized();
        Map<DemoGroup, Integer> counts = new EnumMap<>(DemoGroup.class);
        for (DemoGroup group : DemoGroup.values()) counts.put(group, 0);
        for (int candidate = 1; counts.values().stream().anyMatch(value -> value < 10); candidate++) {
            String userId = String.format("demoUser%04d", candidate);
            DemoGroup group = groupFor(experimentId, userId);
            int index = counts.get(group);
            if (index >= 10) continue;
            counts.put(group, index + 1);
            String checkoutId = "fixture-" + userId;
            CheckoutAttempt attempt = store.find(experimentId, checkoutId).orElse(null);
            if (attempt == null) {
                attempt = evaluateCheckout(experimentId, checkoutId, userId,
                        new BigDecimal("120.00"), new BigDecimal("30.00"), ZERO);
            }
            if (!attempt.isSettled() && "SUCCEEDED".equals(attempt.getDecisionStatus())) {
                int paidLimit = "HIGH_VALUE_OFFER".equals(attempt.getDecisionConclusion()) ? 4
                        : "LOW_VALUE_OFFER".equals(attempt.getDecisionConclusion()) ? 3 : 2;
                boolean paid = index < paidLimit;
                feedback(experimentId, checkoutId, checkoutId + "-settled", "SETTLED",
                        paid, paid ? attempt.getRecommendedOfferCode() : "NONE");
            }
        }
        return report(experimentId);
    }

    /** {@inheritDoc} 只读取当前进程内的模拟反馈，不触发新的决策或结算。 */
    @Override
    public MarketingDemoReport report(String experimentId) {
        requireCode(experimentId, "experimentId");
        return metrics.summarize(experimentId, store.findByExperiment(experimentId));
    }

    /** 同一实验和用户始终映射到相同的三组之一。 */
    private static DemoGroup groupFor(String experimentId, String userId) {
        return DemoGroup.values()[Math.floorMod((experimentId + ":" + userId).hashCode(), 3)];
    }

    /** 将外层业务结论映射为演示用推荐券码。 */
    private static String offerForConclusion(String conclusion) {
        if ("LOW_VALUE_OFFER".equals(conclusion)) return MarketingDemoFlows.LOW_OFFER;
        if ("HIGH_VALUE_OFFER".equals(conclusion)) return MarketingDemoFlows.HIGH_OFFER;
        return "NONE";
    }

    /**
     * 计算唯一实际使用优惠的商家成本；已有优惠与本次推荐券互斥。
     * 未推荐的新券或不存在的已有优惠不得作为实际用券回传。
     */
    private static BigDecimal discountFor(CheckoutAttempt attempt, String offerCode) {
        if ("NONE".equals(offerCode)) return ZERO;
        if ("EXISTING".equals(offerCode) && attempt.getExistingOfferAmount().signum() > 0) {
            return attempt.getExistingOfferAmount();
        }
        if (offerCode.equals(attempt.getRecommendedOfferCode())) {
            if (MarketingDemoFlows.LOW_OFFER.equals(offerCode)) return LOW_DISCOUNT;
            if (MarketingDemoFlows.HIGH_OFFER.equals(offerCode)) return HIGH_DISCOUNT;
        }
        throw new IllegalArgumentException("Applied offer is not available for this checkout");
    }

    /** 校验演示标识的长度、首字符和允许字符。 */
    private static void requireCode(String value, String field) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    /** 校验非负且最多两位小数的模拟金额。 */
    private static void requireAmount(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.scale() > 2) {
            throw new IllegalArgumentException("Invalid " + field);
        }
    }
}
