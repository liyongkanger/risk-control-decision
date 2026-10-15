import { afterEach, expect, it, vi } from 'vitest'
import { listComponentDefinitions, searchComponentDefinitions } from './componentCatalog'

afterEach(() => vi.unstubAllGlobals())

it('reads component fields and default config from backend catalog', async () => {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({
    code: 'SUCCESS', message: '成功', data: [{ key: 'get-variable', condition: false,
      displayName: '获取变量', configFields: [{ name: 'variableCode', type: 'STRING', required: true }],
      defaultConfig: { variableCode: '', versionId: 0, inputMappings: {} } }],
  }) })
  vi.stubGlobal('fetch', fetchMock)
  expect((await listComponentDefinitions())[0].defaultConfig.versionId).toBe(0)
  expect(fetchMock).toHaveBeenCalledWith('/api/components')
})

it('requests server pagination and node-kind filtering for the management page', async () => {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({
    code: 'SUCCESS', message: '成功', data: { records: [], total: 0, page: 2, pageSize: 20 },
  }) })
  vi.stubGlobal('fetch', fetchMock)
  await searchComponentDefinitions('变量', 'CONDITION', 2, 20)
  expect(fetchMock).toHaveBeenCalledWith(
    '/api/components/page?keyword=%E5%8F%98%E9%87%8F&nodeKind=CONDITION&page=2&pageSize=20',
  )
  await searchComponentDefinitions('', '', 1, 10, 'RULE')
  expect(fetchMock).toHaveBeenLastCalledWith(
    '/api/components/page?keyword=&nodeKind=&page=1&pageSize=10&category=RULE',
  )
})
