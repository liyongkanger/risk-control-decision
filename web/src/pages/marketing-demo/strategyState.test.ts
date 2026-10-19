import { expect, it } from 'vitest'
import { pendingStrategySteps } from './strategyState'
import type { DemoStatus } from '../../services/marketingDemo'

const ready: DemoStatus = {
  initialized: true, ruleCurrentVersionId: 1,
  eligibilityCurrentVersionId: 2, eligibilityPinnedRuleVersionId: 1,
  selectionCurrentVersionId: 3, outerCurrentVersionId: 4, outerCurrentVersionNumber: 1,
  outerPinnedEligibilityVersionId: 2, outerPinnedSelectionVersionId: 3,
  sceneSelectedOuterVersionId: 4, sceneSelectedOuterVersionNumber: 1,
  runningEligibilityVersionId: 2, runningSelectionVersionId: 3, runningRuleVersionId: 1,
}

it('distinguishes an initialized running chain from unpublished dependencies', () => {
  expect(pendingStrategySteps(ready)).toEqual([])
  expect(pendingStrategySteps({ ...ready, ruleCurrentVersionId: 5,
    eligibilityCurrentVersionId: 6, outerPinnedEligibilityVersionId: 2,
    outerCurrentVersionId: 7, sceneSelectedOuterVersionId: 4 })
    .map((step) => step.kind)).toEqual(['ELIGIBILITY', 'OUTER', 'ACTIVATE'])
  expect(pendingStrategySteps({ ...ready, initialized: false }).map((step) => step.kind))
    .toEqual(['INITIALIZE'])
})
