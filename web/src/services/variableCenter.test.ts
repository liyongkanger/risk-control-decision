import { afterEach, expect, it, vi } from 'vitest'
import { createVariable, listVariables, testVariable, updateVariable } from './variableCenter'

afterEach(() => vi.unstubAllGlobals())

it('uses the variable center contract for pagination, versions, and test values', async () => {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({
    code: 'SUCCESS', message: '成功', data: { records: [], total: 0, page: 2, pageSize: 20 },
  }) })
  vi.stubGlobal('fetch', fetchMock)
  await listVariables('margin', 2, 20)
  expect(fetchMock).toHaveBeenCalledWith('/api/variables?keyword=margin&page=2&pageSize=20')
  const definition = { variableCode: 'margin', variableName: '毛利', valueType: 'DECIMAL' as const,
    sourceType: 'HTTP_GET' as const, url: 'http://localhost:9199/margin', timeoutMs: 1000,
    parameterNames: ['userId'], resultPath: 'data.margin' }
  await createVariable(definition)
  expect(fetchMock).toHaveBeenLastCalledWith('/api/variables', expect.objectContaining({ method: 'POST' }))
  await updateVariable('margin', 1, definition)
  expect(fetchMock).toHaveBeenLastCalledWith('/api/variables/margin', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"expectedVersion":1'),
  }))
  await testVariable('margin', { userId: 'u1' })
  expect(fetchMock).toHaveBeenLastCalledWith('/api/variables/margin/test', expect.objectContaining({
    method: 'POST', body: '{"input":{"userId":"u1"}}',
  }))
})
