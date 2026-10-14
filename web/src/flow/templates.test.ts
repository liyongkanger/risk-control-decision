import { expect, it } from 'vitest'
import { allNodeFlow, demoFlow, emptyFlow } from './templates'

it('starts a new scene with only start and end nodes', () => {
  const graph = emptyFlow()
  expect(graph.nodes.map((node) => node.kind)).toEqual(['START', 'END'])
  expect(graph.edges).toEqual([])
})

it('keeps the demo component as an explicit template', () => {
  expect(demoFlow().nodes.map((node) => node.id)).toEqual(['start', 'emit', 'result', 'end'])
  expect(demoFlow().edges).toHaveLength(3)
})

it('provides a complete graph using every supported node kind', () => {
  const graph = allNodeFlow()
  expect(new Set(graph.nodes.map((node) => node.kind))).toEqual(new Set([
    'START', 'END', 'COMPONENT', 'PARALLEL_SPLIT', 'PARALLEL_JOIN', 'CONDITION', 'CONDITION_JOIN', 'RESULT',
  ]))
  expect(graph.nodes).toHaveLength(14)
  expect(graph.edges).toHaveLength(15)
  expect(graph.nodes.filter((node) => node.kind === 'RESULT').map((node) => node.id).sort())
    .toEqual(['falseResult', 'trueResult'])
  expect(graph.edges.filter((edge) => edge.source === 'condition').map((edge) => edge.branch).sort())
    .toEqual(['false', 'true'])
  expect(allNodeFlow()).not.toBe(graph)
})
