import { expect, it } from 'vitest'
import { parseRuleTestInput } from './ruleInput'
import type { RuleDefinition } from '../../services/ruleCenter'

const definition: RuleDefinition = {
  inputs: [{ name: 'amount', type: 'DECIMAL', variableBinding: {
    variableCode: 'amount', variableVersionId: 1 } }], resultCodes: ['PASS'], rows: [],
  defaultResult: { resultCode: 'PASS', reasonCode: 'OK', reason: '通过' },
}

it('keeps long decimal text intact and rejects browser-rounded numeric input', () => {
  expect(parseRuleTestInput('{"amount":"0.10000000000000001"}', definition).amount)
    .toBe('0.10000000000000001')
  expect(() => parseRuleTestInput('{"amount":0.10000000000000001}', definition))
    .toThrow('字符串')
  expect(parseRuleTestInput('{"amount":100}', definition).amount).toBe(100)
})
