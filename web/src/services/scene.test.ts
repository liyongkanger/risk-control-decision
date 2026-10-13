import { afterEach, expect, it, vi } from 'vitest'
import { bindSceneFlow, createScene, listScenes, updateScene } from './scene'

afterEach(() => vi.unstubAllGlobals())

it('loads a paged scene list from the shared response envelope', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
    code: 'SUCCESS', message: '成功', data: {
      records: [{ sceneCode: 'payment', sceneName: '支付风控', description: null, publishedVersionNumber: 2, updatetime: '2026-10-13T10:00:00' }],
      total: 1, page: 1, pageSize: 20,
    },
  }), { status: 200 })))

  const page = await listScenes('payment', 1, 20)
  expect(page.total).toBe(1)
  expect(page.records[0].publishedVersionNumber).toBe(2)
  expect(fetch).toHaveBeenCalledWith('/api/scenes?keyword=payment&page=1&pageSize=20')
})

it('creates a scene and edits only its name and description', async () => {
  const mockedFetch = vi.fn(async () => new Response(JSON.stringify({
    code: 'SUCCESS', message: '成功', data: {
      sceneCode: 'payment', sceneName: '支付风控', description: null, publishedVersionNumber: null, updatetime: '2026-10-13T10:00:00',
    },
  }), { status: 200 }))
  vi.stubGlobal('fetch', mockedFetch)

  await createScene({ sceneCode: 'payment', sceneName: '支付风控', description: null })
  await updateScene('payment', { sceneName: '支付决策', description: '新版' })

  expect(mockedFetch).toHaveBeenNthCalledWith(1, '/api/scenes', expect.objectContaining({ method: 'POST' }))
  expect(mockedFetch).toHaveBeenNthCalledWith(2, '/api/scenes/payment', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ sceneName: '支付决策', description: '新版' }),
  }))
})

it('binds an existing flow to a scene without changing scene metadata', async () => {
  const mockedFetch = vi.fn(async () => new Response(JSON.stringify({
    code: 'SUCCESS', message: '成功', data: {
      sceneCode: 'checkout', sceneName: '交易', flowCode: 'sharedRisk', description: null,
      publishedVersionNumber: 2, updatetime: '2026-10-13T16:00:00',
    },
  }), { status: 200 }))
  vi.stubGlobal('fetch', mockedFetch)

  const scene = await bindSceneFlow('checkout', 'sharedRisk')
  expect(scene.flowCode).toBe('sharedRisk')
  expect(mockedFetch).toHaveBeenCalledWith('/api/scenes/checkout/entry-flow', expect.objectContaining({
    method: 'PUT', body: JSON.stringify({ flowCode: 'sharedRisk' }),
  }))
})
