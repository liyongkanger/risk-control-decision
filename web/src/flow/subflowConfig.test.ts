import { expect, it } from 'vitest'
import { parseRequiredInputs, parseJsonObject, selectPublishedVersion, isVersionOutdated, readSubflowConfig } from './subflowConfig'

it('parses unique required input identifiers from commas and lines', () => {
  expect(parseRequiredInputs(' userId, orderAmount\nuserId ')).toEqual(['userId', 'orderAmount'])
  expect(() => parseRequiredInputs('user-id')).toThrow('输入字段名')
})

it('rejects JSON arrays and primitives where an input object is needed', () => {
  expect(parseJsonObject('{"positive":true}')).toEqual({ positive: true })
  expect(() => parseJsonObject('[]')).toThrow('JSON 对象')
})

it('keeps matching mappings when upgrading a pinned child version', () => {
  const selected = selectPublishedVersion({
    flowCode: 'child', versionId: 41,
    inputMappings: {
      userId: { source: 'REQUEST_INPUT', key: 'userId' },
      removed: { source: 'REQUEST_INPUT', key: 'oldField' },
    },
  }, { id: 42, flowCode: 'child', versionNumber: 2, requiredInputs: ['userId', 'amount'] })
  expect(selected).toEqual({ flowCode: 'child', versionId: 42,
    inputMappings: { userId: { source: 'REQUEST_INPUT', key: 'userId' } } })
  expect(isVersionOutdated({ flowCode: 'child', versionId: 41, inputMappings: {} },
    { id: 42, flowCode: 'child', versionNumber: 2, requiredInputs: [] })).toBe(true)
})

it('ignores malformed draft mappings so the inspector remains editable', () => {
  expect(readSubflowConfig({ flowCode: 'child', versionId: 41,
    inputMappings: { positive: null, amount: { source: 'REQUEST_INPUT', key: 'orderAmount' } } }))
    .toEqual({ flowCode: 'child', versionId: 41,
      inputMappings: { amount: { source: 'REQUEST_INPUT', key: 'orderAmount' } } })
})
