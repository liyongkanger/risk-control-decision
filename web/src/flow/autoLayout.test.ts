import { describe, expect, it } from 'vitest'
import { arrangeGraph, isVerticalLayout } from './autoLayout'
import { allNodeFlow } from './templates'
import type { GraphDefinition } from './types'

describe('one-click graph arrangement', () => {
  it('keeps parallel and conditional paths separate while every edge flows downward', () => {
    const graph = allNodeFlow()
    const original = structuredClone(graph)
    const layout = arrangeGraph(graph)

    expect(Object.keys(layout)).toHaveLength(graph.nodes.length)
    expect(graph).toEqual(original)
    expect(layout.parallelLeft.x).toBeLessThan(layout.parallelRight.x)
    expect(layout.truePath.x).toBeLessThan(layout.falsePath.x)
    expect(layout.parallelJoin.x).toBeGreaterThan(layout.parallelLeft.x)
    expect(layout.parallelJoin.x).toBeLessThan(layout.parallelRight.x)
    for (const edge of graph.edges) {
      expect(layout[edge.target].y, edge.id).toBeGreaterThan(layout[edge.source].y)
    }
    expect(new Set(Object.values(layout).map(({ x, y }) => `${x},${y}`)).size).toBe(graph.nodes.length)
    expect(isVerticalLayout({ ...graph, layout })).toBe(true)
    expect(isVerticalLayout(graph)).toBe(false)
  })

  it('handles nested branches and places merges between their incoming paths', () => {
    const ids = ['start', 'eligibilityCall', 'selectionCall', 'highCheck', 'highResult',
      'lowCheck', 'lowResult', 'noResult', 'lowJoin', 'highJoin', 'end']
    const connections = [
      ['start', 'eligibilityCall', null], ['eligibilityCall', 'selectionCall', null],
      ['selectionCall', 'highCheck', null], ['highCheck', 'highResult', 'true'],
      ['highCheck', 'lowCheck', 'false'], ['lowCheck', 'lowResult', 'true'],
      ['lowCheck', 'noResult', 'false'], ['lowResult', 'lowJoin', null],
      ['noResult', 'lowJoin', null], ['lowJoin', 'highJoin', null],
      ['highResult', 'highJoin', null], ['highJoin', 'end', null],
    ] as const
    const graph: GraphDefinition = {
      schemaVersion: 1, layout: {}, requiredInputs: [],
      nodes: ids.map((id) => ({ id, kind: 'COMPONENT', componentKey: null, failurePolicy: null, config: {} })),
      edges: connections.map(([source, target, branch], index) => ({
        id: `e${index}`, source, target, branch,
      })),
    }
    const layout = arrangeGraph(graph)
    expect(layout.highResult.x).toBeLessThan(layout.lowCheck.x)
    expect(layout.lowResult.x).toBeLessThan(layout.noResult.x)
    expect(layout.lowJoin.x).toBeGreaterThan(layout.lowResult.x)
    expect(layout.lowJoin.x).toBeLessThan(layout.noResult.x)
    expect(layout.highJoin.x).toBeGreaterThan(layout.highResult.x)
    expect(layout.highJoin.x).toBeLessThan(layout.lowJoin.x)
  })

  it('rejects cycles instead of placing nodes on top of one another', () => {
    const graph: GraphDefinition = { schemaVersion: 1, layout: {}, nodes: [
      { id: 'a', kind: 'START', componentKey: null, failurePolicy: null, config: {} },
      { id: 'b', kind: 'END', componentKey: null, failurePolicy: null, config: {} },
    ], edges: [
      { id: 'ab', source: 'a', target: 'b', branch: null },
      { id: 'ba', source: 'b', target: 'a', branch: null },
    ] }
    expect(() => arrangeGraph(graph)).toThrow('环路')
  })
})
