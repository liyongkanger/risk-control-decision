import { afterEach, expect, it, vi } from 'vitest'
import { createVariable, listVariableCategories, listVariables, testVariable, updateVariable,
  updateVariableCategory } from './variableCenter'

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
    parameterNames: ['userId'], dependencyVersionIds: { userId: 12 }, resultPath: 'data.margin' }
  await createVariable(definition)
  expect(fetchMock).toHaveBeenLastCalledWith('/api/variables', expect.objectContaining({
    method: 'POST', body: expect.stringContaining('"dependencyVersionIds":{"userId":12}'),
  }))
  await updateVariable('margin', 1, definition)
  expect(fetchMock).toHaveBeenLastCalledWith('/api/variables/margin', expect.objectContaining({
    method: 'PUT', body: expect.stringContaining('"expectedVersion":1'),
  }))
  await testVariable('margin', { userId: 'u1' })
  expect(fetchMock).toHaveBeenLastCalledWith('/api/variables/margin/test', expect.objectContaining({
    method: 'POST', body: '{"input":{"userId":"u1"}}',
  }))
  await createVariable({ variableCode: 'demoOrderAmount', variableName: '演示订单金额',
    valueType: 'DECIMAL', sourceType: 'DEMO_MARKETING', url: 'marketing://checkout',
    timeoutMs: 1000, parameterNames: ['uid', 'checkoutId', 'experimentId'],
    dependencyVersionIds: { uid: 1, checkoutId: 2, experimentId: 3 }, resultPath: 'orderAmount' })
  expect(fetchMock).toHaveBeenLastCalledWith('/api/variables', expect.objectContaining({
    method: 'POST', body: expect.stringContaining('"sourceType":"DEMO_MARKETING"'),
  }))
  await createVariable({ variableCode: 'userId', variableName: '用户 ID', valueType: 'STRING',
    sourceType: 'REQUEST_INPUT', url: '', timeoutMs: 1000, parameterNames: [],
    dependencyVersionIds: {}, resultPath: '' })
  expect(fetchMock).toHaveBeenLastCalledWith('/api/variables', expect.objectContaining({
    body: expect.stringContaining('"sourceType":"REQUEST_INPUT"'),
  }))
})

it('loads category options and sends all list filters to server pagination', async () => {
  const categories = [{ code: 'USER', name: '用户' }]
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({
    code: 'SUCCESS', message: '成功', data: categories,
  }) })
  vi.stubGlobal('fetch', fetchMock)
  expect(await listVariableCategories()).toEqual(categories)
  expect(fetchMock).toHaveBeenLastCalledWith('/api/variables/categories')
  await listVariables('uid', 2, 20, { categoryCode: 'USER', sourceType: 'REQUEST_INPUT', valueType: 'STRING' })
  expect(fetchMock).toHaveBeenLastCalledWith(
    '/api/variables?keyword=uid&page=2&pageSize=20&categoryCode=USER&sourceType=REQUEST_INPUT&valueType=STRING',
  )
})

it('creates with a category and edits category without a version update', async () => {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({
    code: 'SUCCESS', message: '成功', data: { variableCode: 'uid', categoryCode: 'USER', versionNumber: 1, id: 8 },
  }) })
  vi.stubGlobal('fetch', fetchMock)
  await createVariable({ variableCode: 'uid', variableName: '用户', valueType: 'STRING',
    sourceType: 'REQUEST_INPUT', url: '', timeoutMs: 1000, parameterNames: [],
    dependencyVersionIds: {}, resultPath: '', categoryCode: 'USER' })
  expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toMatchObject({ categoryCode: 'USER' })
  await updateVariableCategory('uid', 'OTHER')
  expect(fetchMock).toHaveBeenLastCalledWith('/api/variables/uid/category', expect.objectContaining({
    method: 'PUT', body: '{"categoryCode":"OTHER"}',
  }))
})
