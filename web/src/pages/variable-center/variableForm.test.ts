import { expect, it } from 'vitest'
import { toCreateVariableInput, toVariableInput } from './variableForm'

it('omits external source fields for request and context variables', () => {
  for (const sourceType of ['REQUEST_INPUT', 'FLOW_CONTEXT'] as const) {
    expect(toVariableInput({ variableCode: 'userId', variableName: '用户', valueType: 'STRING', sourceType,
      url: 'http://stale', timeoutMs: 500, parameters: 'other', resultPath: 'data.value' })).toEqual({
      variableCode: 'userId', variableName: '用户', valueType: 'STRING', sourceType,
      url: '', timeoutMs: 1000, parameterNames: [], dependencyVersionIds: {}, resultPath: '',
    })
  }
})

it('includes the explicitly chosen category only when creating a variable', () => {
  const value = { variableCode: 'uid', variableName: '用户', valueType: 'STRING' as const,
    sourceType: 'REQUEST_INPUT' as const, url: '', timeoutMs: 1000, parameters: '', resultPath: '',
    categoryCode: 'USER' as const }
  expect(toCreateVariableInput(value).categoryCode).toBe('USER')
  expect(toVariableInput(value)).not.toHaveProperty('categoryCode')
})

it('keeps external variable dependencies by variable code', () => {
  const input = toVariableInput({ variableCode: 'amount', variableName: '金额', valueType: 'DECIMAL',
    sourceType: 'HTTP_GET', url: 'http://localhost/amount', timeoutMs: 900,
    parameters: 'userId, checkoutId', resultPath: 'data.amount' },
  { userId: 12, checkoutId: 13 })
  expect(input.parameterNames).toEqual(['userId', 'checkoutId'])
  expect(input.dependencyVersionIds).toEqual({ userId: 12, checkoutId: 13 })
})
