import { readApi } from './http'

/** 固定营销演示接口，订单和反馈在后端当前进程内模拟。 */
export type DemoGroup = 'CONTROL' | 'LOW' | 'HIGH'
export type DemoCheckout = {
  experimentId: string; checkoutId: string; userId: string; group: DemoGroup
  requestId: string; decidedAt: string; outerVersionId: number; subflowVersionIds: Record<string, number>
  ruleSetCode: string | null; ruleVersionId: number | null
  matchedRuleId: string | null; ruleReasonCode: string | null
  decisionStatus: string; decisionConclusion: string | null; decisionReason: string | null
  recommendedOfferCode: string; eligible: boolean
  orderAmount: number; preDiscountMargin: number; existingOfferAmount: number
  settled: boolean; paid: boolean; refunded: boolean; actualOfferCode: string
  actualDiscountAmount: number; contributionProfit: number
}
export type DemoMetrics = {
  group: DemoGroup; users: number; paidUsers: number; refundedUsers: number
  merchantDiscount: number; totalContributionProfit: number
  averageContributionProfit: number; paymentConversionRate: number
  incrementalAverageContributionProfit: number | null
  refundRate: number | null; recommendedOfferUseRate: number | null
}
export type DemoStatus = {
  initialized: boolean
  ruleCurrentVersionId: number | null
  eligibilityCurrentVersionId: number | null; eligibilityPinnedRuleVersionId: number | null
  selectionCurrentVersionId: number | null
  outerCurrentVersionId: number | null; outerCurrentVersionNumber: number | null
  outerPinnedEligibilityVersionId: number | null; outerPinnedSelectionVersionId: number | null
  sceneSelectedOuterVersionId: number | null; sceneSelectedOuterVersionNumber: number | null
  runningEligibilityVersionId: number | null; runningSelectionVersionId: number | null
  runningRuleVersionId: number | null
}
export type DemoReport = {
  experimentId: string; groups: Record<DemoGroup, DemoMetrics>; attempts: DemoCheckout[]
}
export type DemoSetup = {
  sceneCode: string; eligibilityFlowCode: string; eligibilityVersionId: number
  selectionFlowCode: string; selectionVersionId: number
  outerFlowCode: string; outerVersionId: number
}
export type CheckoutInput = {
  experimentId: string; checkoutId: string; userId: string
  orderAmount: number; preDiscountMargin: number; existingOfferAmount: number
}
export type FeedbackInput = {
  experimentId: string; checkoutId: string; eventId: string
  eventType: 'SETTLED' | 'REFUNDED'; paid?: boolean; appliedOfferCode?: string
}

const base = '/api/demo/checkout-offer'
const post = async <T>(path: string, body?: object) => readApi<T>(await fetch(`${base}${path}`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: body === undefined ? undefined : JSON.stringify(body),
}))

export const setupMarketingDemo = () => post<DemoSetup>('/setup')
/** 只读查询当前真正运行的规则与流程版本链。 */
export const getMarketingDemoStatus = async () => readApi<DemoStatus>(await fetch(`${base}/status`))
export const checkoutMarketingDemo = (input: CheckoutInput) => post<DemoCheckout>('/checkout', input)
export const feedbackMarketingDemo = (input: FeedbackInput) => post<DemoCheckout>('/feedback', input)
export const runMarketingFixture = (experimentId: string) => post<DemoReport>('/run-fixture', { experimentId })
export const getMarketingReport = async (experimentId: string) => readApi<DemoReport>(await fetch(
  `${base}/report?${new URLSearchParams({ experimentId })}`,
))
