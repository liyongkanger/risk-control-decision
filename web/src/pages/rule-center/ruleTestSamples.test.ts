import { expect, it } from 'vitest'
import type { RuleDefinition } from '../../services/ruleCenter'
import type { VariableVersion } from '../../services/variableCenter'
import { buildRuleTestSamples, buildRuleTrialSetup, retainRuleTestValues,
  setRuleTrialField } from './ruleTestSamples'

const definition = (id: number, code: string): RuleDefinition => ({
  inputs: [{ name: 'value', type: 'DECIMAL',
    variableBinding: { variableCode: code, variableVersionId: id } }],
  resultCodes: ['PASS'], rows: [],
  defaultResult: { resultCode: 'PASS', reasonCode: 'DEFAULT', reason: '通过' },
})

const version = (id: number, code: string, sourceType: VariableVersion['sourceType'],
  valueType: VariableVersion['valueType'], dependencies: Record<string, number> = {}) => ({
    id, variableCode: code, sourceType, valueType, dependencyVersionIds: dependencies,
  }) as VariableVersion

it('uses request dependencies of external variables for the trial input', async () => {
  const versions = new Map([
    [10, version(10, 'remoteAmount', 'HTTP_GET', 'DECIMAL', { uid: 11, checkoutId: 12 })],
    [11, version(11, 'uid', 'REQUEST_INPUT', 'STRING')],
    [12, version(12, 'checkoutId', 'REQUEST_INPUT', 'STRING')],
  ])
  const samples = await buildRuleTestSamples(definition(10, 'remoteAmount'), async (id) => versions.get(id)!)

  expect(samples).toEqual({ input: { uid: '', checkoutId: '' }, contextValues: {} })
})

it('places flow context values in contextValues, including nested external dependencies', async () => {
  const versions = new Map([
    [20, version(20, 'remoteScore', 'HTTP_GET', 'DECIMAL', { upstreamScore: 21 })],
    [21, version(21, 'upstreamScore', 'FLOW_CONTEXT', 'DECIMAL')],
  ])
  const samples = await buildRuleTestSamples(definition(20, 'remoteScore'), async (id) => versions.get(id)!)

  expect(samples).toEqual({ input: {}, contextValues: { upstreamScore: '0' } })
})

it('retains user-entered trial values when a second variable is selected', () => {
  expect(retainRuleTestValues(
    { input: { uid: '', checkoutId: '' }, contextValues: { score: '0' } },
    { input: { uid: 'real-user', stale: 'unused' }, contextValues: { score: '73' } },
  )).toEqual({ input: { uid: 'real-user', checkoutId: '' }, contextValues: { score: '73' } })
})

it('describes editable request and context fields behind external variables', async () => {
  const versions = new Map([
    [30, version(30, 'remoteScore', 'HTTP_GET', 'DECIMAL', { uid: 31, localScore: 32 })],
    [31, version(31, 'uid', 'REQUEST_INPUT', 'STRING')],
    [32, version(32, 'localScore', 'FLOW_CONTEXT', 'DECIMAL')],
  ])
  const setup = await buildRuleTrialSetup(definition(30, 'remoteScore'), async (id) => versions.get(id)!)
  expect(setup.fields).toEqual([
    { code: 'uid', source: 'REQUEST_INPUT', valueType: 'STRING' },
    { code: 'localScore', source: 'FLOW_CONTEXT', valueType: 'DECIMAL' },
  ])
  expect(setup.samples).toEqual({ input: { uid: '' }, contextValues: { localScore: '0' } })
})

it('updates one trial field while keeping decimal text and other input values', () => {
  expect(setRuleTrialField('{"uid":"u1","amount":"0.10000000000000001"}', 'uid', 'u2'))
    .toEqual('{\n  "uid": "u2",\n  "amount": "0.10000000000000001"\n}')
})
