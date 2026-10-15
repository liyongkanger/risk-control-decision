import { afterEach, expect, it, vi } from 'vitest'
import { createRuleSet, getRuleSetVersion, listRuleSets, publishRuleSet, testRuleSet, testRuleSetVersion,
  updateRuleDraft } from './ruleCenter'

afterEach(() => vi.unstubAllGlobals())

it('uses fixed rule versions and draft revisions in requests', async () => {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({
    code: 'SUCCESS', message: '成功', data: { records: [], total: 0, page: 2, pageSize: 20 },
  }) })
  vi.stubGlobal('fetch', fetchMock)
  await listRuleSets('eligibility', 2, 20)
  expect(fetchMock).toHaveBeenCalledWith('/api/rule-sets?keyword=eligibility&page=2&pageSize=20')
  const definition = { inputs: [{ name: 'amount', type: 'DECIMAL' as const }],
    resultCodes: ['ELIGIBLE'], rows: [],
    defaultResult: { resultCode: 'ELIGIBLE', reasonCode: 'QUALIFIED', reason: '通过' } }
  await createRuleSet('eligibility', '资格', definition)
  expect(fetchMock).toHaveBeenLastCalledWith('/api/rule-sets', expect.objectContaining({ method: 'POST' }))
  await updateRuleDraft('eligibility', 3, definition)
  expect(fetchMock).toHaveBeenLastCalledWith('/api/rule-sets/eligibility/draft', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"expectedRevision":3'),
  }))
  await testRuleSet('eligibility', { amount: 100 })
  expect(fetchMock).toHaveBeenLastCalledWith('/api/rule-sets/eligibility/test', expect.objectContaining({
    method: 'POST', body: '{"input":{"amount":100}}',
  }))
  await testRuleSetVersion('eligibility', 12, { amount: '0.10000000000000001' })
  expect(fetchMock).toHaveBeenLastCalledWith('/api/rule-sets/eligibility/versions/12/test',
    expect.objectContaining({ method: 'POST', body: '{"input":{"amount":"0.10000000000000001"}}' }))
  await publishRuleSet('eligibility', 3)
  expect(fetchMock).toHaveBeenLastCalledWith('/api/rule-sets/eligibility/publish', expect.objectContaining({
    method: 'POST', body: '{"expectedRevision":3}',
  }))
  await getRuleSetVersion(12)
  expect(fetchMock).toHaveBeenLastCalledWith('/api/rule-sets/versions/12')
})
