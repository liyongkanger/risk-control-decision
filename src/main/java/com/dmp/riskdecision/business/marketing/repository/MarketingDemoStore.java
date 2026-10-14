package com.dmp.riskdecision.business.marketing.repository;

import com.dmp.riskdecision.business.marketing.model.CheckoutAttempt;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** 仅保存当前应用进程中的模拟结算和幂等反馈事件。 */
@Repository
public class MarketingDemoStore {
    private final Map<String, CheckoutAttempt> attempts = new ConcurrentHashMap<>();
    private final Map<String, String> eventFingerprints = new ConcurrentHashMap<>();

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
}
