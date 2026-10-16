package com.dmp.riskdecision.business.marketing.repository;

import com.dmp.riskdecision.business.marketing.model.CheckoutAttempt;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.Getter;
import org.springframework.stereotype.Repository;

/** 仅保存当前应用进程中的模拟结算和幂等反馈事件。 */
@Repository
public class MarketingDemoStore {
    private final Map<String, CheckoutAttempt> attempts = new ConcurrentHashMap<>();
    private final Map<String, String> eventFingerprints = new ConcurrentHashMap<>();
    private final Map<String, CheckoutFact> checkoutFacts = new ConcurrentHashMap<>();
    // LiteFlow 全局节点可能属于另一 Spring 上下文；本线程决策显式绑定登记事实的仓储。
    private static final ThreadLocal<MarketingDemoStore> CURRENT = new ThreadLocal<>();

    /** 登记页面提供的本次模拟结算事实，供变量中心按业务标识读取。 */
    public synchronized void stageCheckout(String experimentId, String checkoutId, String uid,
                                           BigDecimal orderAmount, BigDecimal margin,
                                           BigDecimal existingOfferAmount) {
        String key = key(experimentId, checkoutId);
        CheckoutFact next = new CheckoutFact(uid, orderAmount, margin, existingOfferAmount,
                actualDiscountSpent(experimentId));
        CheckoutFact prior = checkoutFacts.putIfAbsent(key, next);
        if (prior != null && !prior.sameValues(next)) {
            throw new IllegalStateException("Checkout ID already has different staged facts");
        }
    }

    /** 返回本线程决策正在使用的仓储；普通变量试取值可回退注入的本地仓储。 */
    public static MarketingDemoStore currentForThread() { return CURRENT.get(); }

    /** 只在同步决策执行范围内绑定仓储，结束后恢复原线程状态。 */
    public <T> T withCurrentFacts(Supplier<T> decision) {
        MarketingDemoStore previous = CURRENT.get();
        CURRENT.set(this);
        try { return decision.get(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    /** 按实验、结算和用户三重标识读取模拟事实，避免同一用户多笔结算混用。 */
    public CheckoutFact requireStaged(String experimentId, String checkoutId, String uid) {
        CheckoutFact fact = checkoutFacts.get(key(experimentId, checkoutId));
        if (fact == null || !fact.getUid().equals(uid)) {
            throw new NoSuchElementException("Marketing checkout facts not found");
        }
        return fact;
    }

    public Optional<CheckoutAttempt> find(String experimentId, String checkoutId) {
        return Optional.ofNullable(attempts.get(key(experimentId, checkoutId)));
    }

    /** 相同结算 ID 只保留一次决策，输入不一致时拒绝覆盖。 */
    public synchronized CheckoutAttempt save(CheckoutAttempt candidate) {
        String key = key(candidate.getExperimentId(), candidate.getCheckoutId());
        CheckoutAttempt existing = attempts.get(key);
        if (existing != null) {
            if (!existing.sameRequest(candidate.getUserId(), candidate.getOrderAmount(),
                    candidate.getPreDiscountMargin(), candidate.getExistingOfferAmount())) {
                throw new IllegalStateException("Checkout ID already has different inputs");
            }
            return existing;
        }
        attempts.put(key, candidate);
        return candidate;
    }

    public CheckoutAttempt require(String experimentId, String checkoutId) {
        return find(experimentId, checkoutId).orElseThrow(
                () -> new NoSuchElementException("Demo checkout not found: " + checkoutId));
    }

    /** 事件 ID 与内容一起去重，退款只修改已有结算的利润。 */
    public synchronized CheckoutAttempt feedback(String experimentId, String checkoutId, String eventId,
                                                  String eventType, Boolean paid, String offerCode,
                                                  BigDecimal discountAmount) {
        CheckoutAttempt attempt = require(experimentId, checkoutId);
        String eventKey = key(experimentId, checkoutId) + ":" + eventId;
        String fingerprint = checkoutId + "|" + eventType + "|" + paid + "|" + offerCode;
        String previous = eventFingerprints.get(eventKey);
        if (previous != null) {
            if (!previous.equals(fingerprint)) throw new IllegalStateException("Event ID payload conflict");
            return attempt;
        }
        if (!"SUCCEEDED".equals(attempt.getDecisionStatus())) {
            throw new IllegalStateException("Failed decisions cannot receive normal checkout feedback");
        }
        if ("SETTLED".equals(eventType)) {
            if (paid == null || offerCode == null || discountAmount == null) {
                throw new IllegalArgumentException("Settlement requires paid and actual offer");
            }
            attempt.settle(paid, offerCode, discountAmount);
        } else if ("REFUNDED".equals(eventType)) {
            attempt.refund();
        } else {
            throw new IllegalArgumentException("Unsupported feedback event type");
        }
        eventFingerprints.put(eventKey, fingerprint);
        return attempt;
    }

    public List<CheckoutAttempt> findByExperiment(String experimentId) {
        return attempts.values().stream().filter(attempt -> experimentId.equals(attempt.getExperimentId()))
                .sorted(Comparator.comparing(CheckoutAttempt::getCheckoutId)).toList();
    }

    public BigDecimal actualDiscountSpent(String experimentId) {
        return findByExperiment(experimentId).stream()
                .filter(attempt -> attempt.isPaid() && !attempt.isRefunded())
                .map(CheckoutAttempt::getActualDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String key(String experimentId, String id) {
        return experimentId + ":" + id;
    }

    /** 一次结算的原始模拟业务事实；资格计算不在流程请求中传输这些金额。 */
    @Getter
    public static class CheckoutFact {
        private final String uid;
        private final BigDecimal orderAmount;
        private final BigDecimal preDiscountMargin;
        private final BigDecimal existingOfferAmount;
        private final BigDecimal discountSpentAtDecision;

        public CheckoutFact(String uid, BigDecimal orderAmount, BigDecimal preDiscountMargin,
                            BigDecimal existingOfferAmount, BigDecimal discountSpentAtDecision) {
            this.uid = uid;
            this.orderAmount = orderAmount;
            this.preDiscountMargin = preDiscountMargin;
            this.existingOfferAmount = existingOfferAmount;
            this.discountSpentAtDecision = discountSpentAtDecision;
        }

        private boolean sameValues(CheckoutFact other) {
            return uid.equals(other.uid)
                    && orderAmount.compareTo(other.orderAmount) == 0
                    && preDiscountMargin.compareTo(other.preDiscountMargin) == 0
                    && existingOfferAmount.compareTo(other.existingOfferAmount) == 0;
        }
    }
}
