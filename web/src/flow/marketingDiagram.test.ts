import { describe, expect, it } from 'vitest'
import { marketingDiagramLayout, marketingNodeCaption } from './marketingDiagram'
import type { FlowNode, GraphDefinition } from './types'

const ids = ['start', 'eligibilityCall', 'selectionCall', 'highCheck', 'highResult',
  'lowCheck', 'lowResult', 'noResult', 'lowJoin', 'highJoin', 'end']
const node = (id: string, kind: FlowNode['kind'], config: FlowNode['config'] = {}): FlowNode =>
  ({ id, kind, componentKey: null, failurePolicy: null, config })
const graph: GraphDefinition = { schemaVersion: 1, layout: {}, edges: [], nodes: [
  node('start', 'START'),
  node('eligibilityCall', 'SUBFLOW', { flowCode: 'demoOfferEligibility', versionId: 1 }),
  node('selectionCall', 'SUBFLOW', { flowCode: 'demoOfferSelection', versionId: 2 }),
  node('highCheck', 'CONDITION', { expected: 'HIGH_VALUE_OFFER' }),
  node('highResult', 'RESULT', { conclusion: 'HIGH_VALUE_OFFER', details: { offerCode: 'HIGH_18_OFF_100' } }),
  node('lowCheck', 'CONDITION', { expected: 'LOW_VALUE_OFFER' }),
  node('lowResult', 'RESULT', { conclusion: 'LOW_VALUE_OFFER', details: { offerCode: 'LOW_5_OFF_100' } }),
  node('noResult', 'RESULT', { conclusion: 'NO_NEW_OFFER' }),
  node('lowJoin', 'CONDITION_JOIN'), node('highJoin', 'CONDITION_JOIN'), node('end', 'END'),
] }

describe('marketing decision diagram', () => {
  it('places the unsaved demo graph in separate branch lanes within one readable canvas', () => {
    const layout = marketingDiagramLayout('demoCheckoutDecision', graph)
    expect(layout).not.toBeNull()
    expect(Object.keys(layout ?? {})).toEqual(expect.arrayContaining(ids))
    expect(layout!.highResult.x).toBeGreaterThan(layout!.highCheck.x)
    expect(layout!.lowCheck.x).toBeLessThan(layout!.highCheck.x)
    expect(layout!.highResult.y).toBe(layout!.lowCheck.y)
    expect(layout!.lowResult.y).toBeGreaterThan(layout!.lowCheck.y)
    expect(layout!.noResult.x).toBeGreaterThan(layout!.lowResult.x)
    expect(new Set(Object.values(layout!).map(({ x, y }) => `${x},${y}`)).size).toBe(ids.length)
    expect(Math.max(...Object.values(layout!).map(({ y }) => y))).toBeLessThanOrEqual(760)
  })

  it('keeps saved coordinates and leaves unrelated graphs alone', () => {
    expect(marketingDiagramLayout('otherFlow', graph)).toBeNull()
    expect(marketingDiagramLayout('demoCheckoutDecision', {
      ...graph, layout: { start: { x: 17, y: 29 } },
    })).toBeNull()
  })

  it('shows the business meaning and pinned subflow version in the demo nodes', () => {
    expect(marketingNodeCaption('demoCheckoutDecision', graph.nodes[1])).toBe('优惠资格 · #1')
    expect(marketingNodeCaption('demoCheckoutDecision', graph.nodes[2])).toBe('优惠选择 · #2')
    expect(marketingNodeCaption('demoCheckoutDecision', graph.nodes[4])).toBe('推荐 18 元券')
    expect(marketingNodeCaption('demoCheckoutDecision', graph.nodes[6])).toBe('推荐 5 元券')
    expect(marketingNodeCaption('demoCheckoutDecision', graph.nodes[7])).toBe('不新增优惠')
    expect(marketingNodeCaption('otherFlow', graph.nodes[4])).toBeNull()
  })
})
