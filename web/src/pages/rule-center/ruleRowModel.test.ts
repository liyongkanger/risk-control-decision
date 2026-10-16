import { expect, it } from 'vitest'
import type { RuleDefinition } from '../../services/ruleCenter'
import { addAndCondition, changeConditionField, createDraftRuleRow } from './ruleRowModel'

const definition = (): RuleDefinition => ({
  inputs: [
    { name: 'amount', type: 'DECIMAL', variableBinding: { variableCode: 'orderAmount', variableVersionId: 1 } },
    { name: 'eligible', type: 'BOOLEAN', variableBinding: { variableCode: 'eligible', variableVersionId: 2 } },
  ],
  resultCodes: ['PASS', 'REJECT'],
  rows: [{ id: 'rule-1', priority: 10,
    conditions: [{ input: 'amount', operator: 'LT', expected: '100' }],
    result: { resultCode: 'REJECT', reasonCode: 'LOW', reason: '不足' } }],
  defaultResult: { resultCode: 'PASS', reasonCode: 'DEFAULT', reason: '通过' },
})

it('creates a rule row with a unique identifier and the next priority', () => {
  const row = createDraftRuleRow(definition())
  expect(row.id).toBe('rule-2')
  expect(row.priority).toBe(20)
  expect(row.conditions).toEqual([{ input: 'amount', operator: 'EQ', expected: null }])
  expect(row.result.resultCode).toBe('PASS')
})

it('adds a boolean condition without silently choosing false', () => {
  const rule = definition().rows[0]
  const added = addAndCondition(rule, [definition().inputs[1]])
  expect(added.conditions.at(-1)).toEqual({ input: 'eligible', operator: 'EQ', expected: null })
})

it('resets an incompatible operator and value when a condition changes type', () => {
  const rule = definition().rows[0]
  const changed = changeConditionField(rule, 0, 'eligible', definition().inputs)
  expect(changed.conditions[0]).toEqual({ input: 'eligible', operator: 'EQ', expected: null })
})
