import { afterEach, expect, it, vi } from 'vitest'
import { createFlow, getFlow, getPublishedFlow, listFlows } from './flow'

afterEach(() => vi.unstubAllGlobals())

it('queries a paged flow asset list and reads its published version', async () => {
  const mockedFetch = vi.fn(async () => new Response(JSON.stringify({
    code: 'SUCCESS', message: '成功', data: {
      records: [{ id: 12, flowCode: 'sharedRisk', flowName: '共享风控', flowType: 'DECISION',
        publishedVersionId: 41, publishedVersionNumber: 2, updatetime: '2026-10-13T16:00:00' }],
      total: 1, page: 1, pageSize: 10,
    },
  }), { status: 200 }))
  vi.stubGlobal('fetch', mockedFetch)

  const page = await listFlows('risk', 1, 10)
  expect(page.records[0].publishedVersionNumber).toBe(2)
  expect(mockedFetch).toHaveBeenCalledWith('/api/flows?keyword=risk&page=1&pageSize=10')
})

it('creates and loads a flow asset through the documented endpoints', async () => {
  const mockedFetch = vi.fn(async () => new Response(JSON.stringify({
    code: 'SUCCESS', message: '成功', data: {
      id: 12, flowCode: 'sharedRisk', flowName: '共享风控', flowType: 'DECISION',
      publishedVersionId: null, publishedVersionNumber: null, updatetime: '2026-10-13T16:00:00',
    },
  }), { status: 200 }))
  vi.stubGlobal('fetch', mockedFetch)

  await createFlow({ flowCode: 'sharedRisk', flowName: '共享风控' })
  await getFlow('sharedRisk')
  expect(mockedFetch).toHaveBeenNthCalledWith(1, '/api/flows', expect.objectContaining({
    method: 'POST', body: JSON.stringify({ flowCode: 'sharedRisk', flowName: '共享风控' }),
  }))
  expect(mockedFetch).toHaveBeenNthCalledWith(2, '/api/flows/sharedRisk')
})

it('reads the current published version and its required inputs', async () => {
  const mockedFetch = vi.fn(async () => new Response(JSON.stringify({
    code: 'SUCCESS', message: '成功', data: {
      id: 41, flowCode: 'offerEligibility', versionNumber: 2,
      requiredInputs: ['userId', 'orderAmount'],
    },
  }), { status: 200 }))
  vi.stubGlobal('fetch', mockedFetch)

  const published = await getPublishedFlow('offerEligibility')
  expect(published).toEqual({ id: 41, flowCode: 'offerEligibility', versionNumber: 2,
    requiredInputs: ['userId', 'orderAmount'] })
  expect(mockedFetch).toHaveBeenCalledWith('/api/flows/offerEligibility/published')
})
