import { afterEach, describe, expect, it, vi } from 'vitest'
import { checkoutMarketingDemo, getMarketingReport, runMarketingFixture } from './marketingDemo'

afterEach(() => vi.unstubAllGlobals())

describe('marketing demo API', () => {
  it('sends fixed sample and reads report through the shared response envelope', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({
      code: 'SUCCESS', message: 'success', data: { experimentId: 'demoA', groups: {}, attempts: [] },
    }) })
    vi.stubGlobal('fetch', fetchMock)
    await runMarketingFixture('demoA')
    expect(fetchMock).toHaveBeenCalledWith('/api/demo/checkout-offer/run-fixture', expect.objectContaining({
      method: 'POST', body: '{"experimentId":"demoA"}',
    }))
    await getMarketingReport('demoA')
    expect(fetchMock).toHaveBeenLastCalledWith('/api/demo/checkout-offer/report?experimentId=demoA')
  })

  it('sends checkout amounts and propagates business errors', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: false, status: 422, json: async () => ({
      code: 'INVALID_REQUEST', message: 'Invalid orderAmount', data: null,
    }) })
    vi.stubGlobal('fetch', fetchMock)
    await expect(checkoutMarketingDemo({ experimentId: 'demoA', checkoutId: 'order1', userId: 'user1',
      orderAmount: -1, preDiscountMargin: 30, existingOfferAmount: 0 })).rejects.toThrow('Invalid orderAmount')
  })
})
