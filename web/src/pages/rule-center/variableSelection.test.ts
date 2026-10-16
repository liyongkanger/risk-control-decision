import { expect, it } from 'vitest'
import type { RuleDefinition } from '../../services/ruleCenter'
import { isRuleInputReferenced, renameRuleInput, selectRuleVariable } from './variableSelection'

const definition = (): RuleDefinition => ({
  inputs: [], resultCodes: ['PASS', 'REJECT'], rows: [],
  defaultResult: { resultCode: 'PASS', reasonCode: 'DEFAULT', reason: '通过' },
})

it('adds a searched variable as a typed rule input', () => {
  expect(selectRuleVariable(definition(), {
    id: 12, variableCode: 'orderAmount', valueType: 'DECIMAL',
  }).inputs).toEqual([{ name: 'orderAmount', type: 'DECIMAL',
    variableBinding: { variableCode: 'orderAmount', variableVersionId: 12 } }])
})

it('replaces a variable while keeping the rule alias and its conditions', () => {
  const original = definition()
  original.inputs = [{ name: 'amount', type: 'DECIMAL',
    variableBinding: { variableCode: 'oldAmount', variableVersionId: 1 } }]
  original.rows = [{ id: 'min', priority: 10,
    conditions: [{ input: 'amount', operator: 'GTE', expected: '100' }],
    result: { resultCode: 'REJECT', reasonCode: 'LOW', reason: '不足' } }]

  const updated = selectRuleVariable(original, {
    id: 13, variableCode: 'newAmount', valueType: 'DECIMAL',
  }, 0)

  expect(updated.inputs[0]).toEqual({ name: 'amount', type: 'DECIMAL',
    variableBinding: { variableCode: 'newAmount', variableVersionId: 13 } })
  expect(updated.rows[0].conditions[0].input).toBe('amount')
})

it('clears comparisons that are incompatible with the replacement variable type', () => {
  const original = definition()
  original.inputs = [{ name: 'amount', type: 'DECIMAL',
    variableBinding: { variableCode: 'oldAmount', variableVersionId: 1 } }]
  original.rows = [{ id: 'high', priority: 10,
    conditions: [{ input: 'amount', operator: 'GT', expected: '100' }],
    result: { resultCode: 'REJECT', reasonCode: 'HIGH', reason: '超出' } }]

  const updated = selectRuleVariable(original, {
    id: 2, variableCode: 'riskBand', valueType: 'STRING',
  }, 0)

  expect(updated.inputs[0].name).toBe('amount')
  expect(updated.rows[0].conditions[0]).toEqual({ input: 'amount', operator: 'EQ', expected: null })
})

it('renames an input and every condition that references it', () => {
  const original = definition()
  original.inputs = [{ name: 'amount', type: 'DECIMAL',
    variableBinding: { variableCode: 'orderAmount', variableVersionId: 12 } }]
  original.rows = [{ id: 'min', priority: 10,
    conditions: [{ input: 'amount', operator: 'GTE', expected: '100' }],
    result: { resultCode: 'REJECT', reasonCode: 'LOW', reason: '不足' } }]

  const updated = renameRuleInput(original, 0, 'checkoutAmount')

  expect(updated.inputs[0].name).toBe('checkoutAmount')
  expect(updated.rows[0].conditions[0].input).toBe('checkoutAmount')
})

it('rejects an alias already used by another input before changing conditions', () => {
  const original = definition()
  original.inputs = [
    { name: 'amount', type: 'DECIMAL',
      variableBinding: { variableCode: 'orderAmount', variableVersionId: 12 } },
    { name: 'budget', type: 'DECIMAL',
      variableBinding: { variableCode: 'remainingBudget', variableVersionId: 13 } },
  ]
  original.rows = [{ id: 'budget-check', priority: 10,
    conditions: [{ input: 'budget', operator: 'GTE', expected: '20' }],
    result: { resultCode: 'REJECT', reasonCode: 'LOW', reason: '不足' } }]

  expect(() => renameRuleInput(original, 0, 'budget')).toThrow('规则输入名已存在')
  expect(original.rows[0].conditions[0].input).toBe('budget')
})

it('marks variables used by rule conditions before removal', () => {
  const original = definition()
  original.inputs = [{ name: 'amount', type: 'DECIMAL',
    variableBinding: { variableCode: 'orderAmount', variableVersionId: 12 } }]
  original.rows = [{ id: 'min', priority: 10,
    conditions: [{ input: 'amount', operator: 'LT', expected: '100' }],
    result: { resultCode: 'REJECT', reasonCode: 'LOW', reason: '不足' } }]

  expect(isRuleInputReferenced(original, 'amount')).toBe(true)
  expect(isRuleInputReferenced(original, 'unused')).toBe(false)
})
