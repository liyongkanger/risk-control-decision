import { describe, expect, it } from 'vitest'
import type { Graph } from '@antv/x6'
import { toGraphDefinition } from './graphAdapter'

describe('toGraphDefinition', () => {
  it('keeps execution data separate from canvas coordinates and styles', () => {
    const node = {
      id: 'check',
      getData: () => ({ kind: 'COMPONENT', componentKey: 'emit-value', failurePolicy: 'FAIL_FAST', config: { value: 7 } }),
      getPosition: () => ({ x: 140, y: 220 }),
    }
    const edge = {
      id: 'edge1',
      getSourceCellId: () => 'start',
      getTargetCellId: () => 'check',
      getData: () => ({ branch: 'true', stroke: 'red' }),
    }
    const graph = { getNodes: () => [node], getEdges: () => [edge] } as unknown as Graph

    expect(toGraphDefinition(graph)).toEqual({
      schemaVersion: 1,
      nodes: [{ id: 'check', kind: 'COMPONENT', componentKey: 'emit-value', failurePolicy: 'FAIL_FAST', config: { value: 7 } }],
      edges: [{ id: 'edge1', source: 'start', target: 'check', branch: 'true' }],
      layout: { check: { x: 140, y: 220 } },
    })
    expect(toGraphDefinition(graph, ['userId']).requiredInputs).toEqual(['userId'])
    expect(toGraphDefinition(graph, ['userId'], [{ variableCode: 'decision', variableVersionId: 5,
      nodeId: 'check', resultPath: 'value' }]).contextBindings).toEqual([
      { variableCode: 'decision', variableVersionId: 5, nodeId: 'check', resultPath: 'value' },
    ])
  })
})
