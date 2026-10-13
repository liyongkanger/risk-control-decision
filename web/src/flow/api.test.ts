import { afterEach, expect, it, vi } from 'vitest'
import { loadTargetDraft, publishTargetFlow, saveDraft, saveTargetDraft, validateFlow } from './api'

afterEach(() => vi.unstubAllGlobals())

it('surfaces a malformed scene code as an API error rather than a validation result', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
    code: 'INVALID_REQUEST', message: 'Invalid scene code', data: null,
  }), { status: 422, headers: { 'Content-Type': 'application/json' } })))

  await expect(validateFlow('bad scene', { schemaVersion: 1, nodes: [], edges: [], layout: {} }))
    .rejects.toThrow('Invalid scene code')
})

it('unwraps a successful validation response', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
    code: 'SUCCESS', message: '成功', data: { valid: true, issues: [], compiledEl: 'THEN(emit)' },
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

  await expect(validateFlow('scene', { schemaVersion: 1, nodes: [], edges: [], layout: {} }))
    .resolves.toEqual({ valid: true, issues: [], compiledEl: 'THEN(emit)' })
})

it('returns graph issues from a validation response', async () => {
  const issue = { code: 'INVALID_NODE', nodeId: 'node-1', edgeId: null, message: 'Invalid node' }
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
    code: 'GRAPH_INVALID', message: 'Graph invalid', data: { valid: false, issues: [issue], compiledEl: null },
  }), { status: 422, headers: { 'Content-Type': 'application/json' } })))

  await expect(validateFlow('scene', { schemaVersion: 1, nodes: [], edges: [], layout: {} }))
    .resolves.toEqual({ valid: false, issues: [issue], compiledEl: null })
})

it('shows graph issues from the global error response', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
    code: 'GRAPH_INVALID', message: 'Graph invalid',
    data: [{ code: 'DUPLICATE_NODE_ID', nodeId: 'node-1', edgeId: null, message: 'Duplicate node' }],
  }), { status: 422, headers: { 'Content-Type': 'application/json' } })))

  await expect(saveDraft('scene', { schemaVersion: 1, nodes: [], edges: [], layout: {} }, 0))
    .rejects.toThrow('DUPLICATE_NODE_ID: Duplicate node')
})

it('uses independent flow endpoints for its draft and publication', async () => {
  const mockedFetch = vi.fn(async (_url: string, options?: RequestInit) => new Response(JSON.stringify({
    code: 'SUCCESS', message: '成功', data: options?.method === 'POST'
      ? { id: 7, flowCode: 'shared', versionNumber: 1, chainId: 'flow_shared_v1' }
      : { flowCode: 'shared', graph: { schemaVersion: 1, nodes: [], edges: [], layout: {} }, revision: 1 },
  }), { status: 200 }))
  vi.stubGlobal('fetch', mockedFetch)
  const target = { kind: 'flow' as const, code: 'shared' }
  const graph = { schemaVersion: 1 as const, nodes: [], edges: [], layout: {} }

  await loadTargetDraft(target)
  await saveTargetDraft(target, graph, 0)
  await publishTargetFlow(target, 1)
  expect(mockedFetch).toHaveBeenNthCalledWith(1, '/api/flows/shared/draft')
  expect(mockedFetch).toHaveBeenNthCalledWith(2, '/api/flows/shared/draft?expectedRevision=0',
    expect.objectContaining({ method: 'PUT', body: JSON.stringify(graph) }))
  expect(mockedFetch).toHaveBeenNthCalledWith(3, '/api/flows/shared/publish',
    expect.objectContaining({ method: 'POST', body: JSON.stringify({ expectedRevision: 1 }) }))
})
