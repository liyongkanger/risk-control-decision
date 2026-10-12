import { afterEach, expect, it, vi } from 'vitest'
import { validateFlow } from './api'

afterEach(() => vi.unstubAllGlobals())

it('surfaces a malformed scene code as an API error rather than a validation result', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
    code: 'INVALID_REQUEST', message: 'Invalid scene code', issues: [],
  }), { status: 422, headers: { 'Content-Type': 'application/json' } })))

  await expect(validateFlow('bad scene', { schemaVersion: 1, nodes: [], edges: [], layout: {} }))
    .rejects.toThrow('Invalid scene code')
})
