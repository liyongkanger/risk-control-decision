import { afterEach, expect, it, vi } from 'vitest'
import { saveDraft, validateFlow } from './api'

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
