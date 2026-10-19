import type { DemoStatus } from '../../services/marketingDemo'

export type PendingStrategyStep = {
  kind: 'INITIALIZE' | 'ELIGIBILITY' | 'OUTER' | 'ACTIVATE'
  message: string
  href?: string
}

/** 依固定版本引用顺序提示下一版待处理动作，不把运行中的旧版标为故障。 */
export function pendingStrategySteps(status: DemoStatus): PendingStrategyStep[] {
  if (!status.initialized) return [{ kind: 'INITIALIZE', message: '样板尚未形成完整运行链，请先初始化' }]
  const steps: PendingStrategyStep[] = []
  if (status.ruleCurrentVersionId !== status.eligibilityPinnedRuleVersionId) {
    steps.push({ kind: 'ELIGIBILITY', message: '规则已有新版：资格流程需选择新版规则并发布',
      href: '/flows/demoOfferEligibility/editor' })
  }
  if (status.eligibilityCurrentVersionId !== status.outerPinnedEligibilityVersionId
      || status.selectionCurrentVersionId !== status.outerPinnedSelectionVersionId) {
    steps.push({ kind: 'OUTER', message: '子流程已有新版：外层流程需重新选择并发布',
      href: '/flows/demoCheckoutDecision/editor' })
  }
  if (status.outerCurrentVersionId !== status.sceneSelectedOuterVersionId) {
    steps.push({ kind: 'ACTIVATE', message: '外层流程已有新版：需显式选用当前外层版本' })
  }
  return steps
}
