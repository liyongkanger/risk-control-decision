import type { DemoCheckout, DemoGroup, DemoReport } from '../../services/marketingDemo'

export const groupNames: Record<DemoGroup, string> = {
  CONTROL: '对照组', LOW: '低额券组', HIGH: '高额券组',
}

export const conclusionNames: Record<string, string> = {
  NO_NEW_OFFER: '不新增优惠',
  LOW_VALUE_OFFER: '建议低额券',
  HIGH_VALUE_OFFER: '建议高额券',
}

export const offerNames: Record<string, string> = {
  NONE: '未使用优惠', EXISTING: '已有优惠',
  LOW_5_OFF_100: '5 元券', HIGH_18_OFF_100: '18 元券',
}

export function describeConclusion(attempt: DemoCheckout): string {
  if (attempt.decisionStatus !== 'SUCCEEDED') return '决策执行失败'
  return conclusionNames[attempt.decisionConclusion ?? ''] ?? attempt.decisionConclusion ?? '无业务结论'
}

export function describeOffer(code: string): string { return offerNames[code] ?? code }

export function describeRuleMatch(attempt: DemoCheckout): string {
  if (attempt.matchedRuleId) return `命中规则行 ${attempt.matchedRuleId}`
  if (attempt.ruleSetCode && attempt.ruleReasonCode) return '所有规则行未命中，使用未命中结果'
  return '没有可追溯的规则明细'
}

export function describeProfit(attempt: DemoCheckout): string {
  if (!attempt.settled) return '尚未回传实际结算，暂不计算订单利润'
  if (attempt.refunded) return '已全额退款，本笔贡献利润冲回为 0 元'
  if (!attempt.paid) return '未支付，本笔贡献利润为 0 元'
  return `${Number(attempt.preDiscountMargin).toFixed(2)} − ${Number(attempt.actualDiscountAmount).toFixed(2)} = ${Number(attempt.contributionProfit).toFixed(2)} 元`
}

/** 只根据当前实验反馈生成结论，不把固定样本的预期值套用到其他实验。 */
export function reportHeadline(report: DemoReport | null): { title: string; detail: string } {
  if (!report || report.attempts.length === 0) {
    return { title: '先运行样本，看看哪种优惠更有利',
      detail: '固定样本会执行真实决策流程，并用预设支付行为回传模拟结果。' }
  }
  const groups: DemoGroup[] = ['CONTROL', 'LOW', 'HIGH']
  const observed = groups.filter((group) => report.groups[group].users > 0)
  if (observed.length < 2 || report.groups.CONTROL.users === 0) {
    return { title: '已有决策记录，仍需对照组数据',
      detail: '至少有对照组和一个优惠组的合格用户，才能比较人均贡献利润。' }
  }
  const leader = observed.reduce((best, group) => report.groups[group].averageContributionProfit
    > report.groups[best].averageContributionProfit ? group : best)
  const average = Number(report.groups[leader].averageContributionProfit).toFixed(2)
  const leaders = observed.filter((group) => report.groups[group].averageContributionProfit
    === report.groups[leader].averageContributionProfit)
  if (leaders.length > 1) {
    return { title: `${leaders.map((group) => groupNames[group]).join('、')}当前人均贡献利润持平`,
      detail: `均为 ${average} 元/人。继续查看支付反馈和逐笔记录，再判断策略效果。` }
  }
  return { title: `${groupNames[leader]}当前人均贡献利润最高`,
    detail: `${average} 元/人。先看三组结果，再打开一笔记录核对决策建议、实际用券和执行版本。` }
}
