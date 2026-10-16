import { describeConclusion, describeOffer, describeProfit, describeRuleMatch,
  reportHeadline } from './demoPresentation'
import type { DemoCheckout, DemoMetrics, DemoReport } from '../../services/marketingDemo'
import { describe, expect, it } from 'vitest'

const metrics = (group: DemoMetrics['group'], users: number, average: number): DemoMetrics => ({
  group, users, averageContributionProfit: average, paidUsers: 0, refundedUsers: 0,
  merchantDiscount: 0, totalContributionProfit: 0, paymentConversionRate: 0,
  incrementalAverageContributionProfit: null, refundRate: null, recommendedOfferUseRate: null,
})
const report = (control: DemoMetrics, low: DemoMetrics, high: DemoMetrics): DemoReport => ({
  experimentId: 'demo', groups: { CONTROL: control, LOW: low, HIGH: high },
  attempts: [{} as DemoCheckout],
})

describe('marketing demo presentation', () => {
  it('uses live group metrics for the headline', () => {
    expect(reportHeadline(report(metrics('CONTROL', 10, 6), metrics('LOW', 10, 7.5),
      metrics('HIGH', 10, 4.8))).title).toContain('低额券组')
    expect(reportHeadline(report(metrics('CONTROL', 10, 9), metrics('LOW', 10, 7.5),
      metrics('HIGH', 10, 4.8))).title).toContain('对照组')
  })

  it('does not claim a winning strategy without a control comparison', () => {
    expect(reportHeadline(report(metrics('CONTROL', 0, 0), metrics('LOW', 1, 8),
      metrics('HIGH', 0, 0))).title).toContain('对照组数据')
    expect(reportHeadline(report(metrics('CONTROL', 2, 6), metrics('LOW', 2, 6),
      metrics('HIGH', 2, 4))).title).toContain('持平')
  })

  it('translates known decision codes while preserving unknown codes', () => {
    expect(describeConclusion({ decisionStatus: 'SUCCEEDED',
      decisionConclusion: 'LOW_VALUE_OFFER' } as DemoCheckout)).toBe('建议低额券')
    expect(describeOffer('HIGH_18_OFF_100')).toBe('18 元券')
    expect(describeOffer('CUSTOM')).toBe('CUSTOM')
  })

  it('separates unmatched rules from missing trace and paid profit from refunds', () => {
    expect(describeRuleMatch({ ruleSetCode: 'rules', ruleReasonCode: 'PASS',
      matchedRuleId: null } as DemoCheckout)).toContain('所有规则行未命中')
    expect(describeRuleMatch({ ruleSetCode: null, ruleReasonCode: null,
      matchedRuleId: null } as DemoCheckout)).toContain('没有可追溯')
    const paid = { settled: true, paid: true, refunded: false,
      preDiscountMargin: 30, actualDiscountAmount: 5, contributionProfit: 25 } as DemoCheckout
    expect(describeProfit(paid)).toBe('30.00 − 5.00 = 25.00 元')
    expect(describeProfit({ ...paid, refunded: true })).toContain('冲回')
  })
})
