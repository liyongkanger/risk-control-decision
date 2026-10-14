import type { GraphDefinition } from './types'
import allNodeExample from './all-node-example.json'

/** 新场景的空流程图，不自动形成草稿。 */
export function emptyFlow(): GraphDefinition {
  return {
    schemaVersion: 1,
    nodes: [
      { id: 'start', kind: 'START', componentKey: null, failurePolicy: null, config: {} },
      { id: 'end', kind: 'END', componentKey: null, failurePolicy: null, config: {} },
    ],
    edges: [],
    layout: { start: { x: 70, y: 220 }, end: { x: 370, y: 220 } },
  }
}

/** 演示组件流程，只有用户主动选择时才载入画布。 */
export function demoFlow(): GraphDefinition {
  return {
    schemaVersion: 1,
    nodes: [
      { id: 'start', kind: 'START', componentKey: null, failurePolicy: null, config: {} },
      { id: 'emit', kind: 'COMPONENT', componentKey: 'emit-value', failurePolicy: 'FAIL_FAST', config: { value: 'demo' } },
      { id: 'result', kind: 'RESULT', componentKey: null, failurePolicy: null,
        config: { conclusion: 'NO_NEW_OFFER', reasonCode: 'DEMO', reason: '演示流程无新增优惠' } },
      { id: 'end', kind: 'END', componentKey: null, failurePolicy: null, config: {} },
    ],
    edges: [
      { id: 'edge1', source: 'start', target: 'emit', branch: null },
      { id: 'edge2', source: 'emit', target: 'result', branch: null },
      { id: 'edge3', source: 'result', target: 'end', branch: null },
    ],
    layout: { start: { x: 30, y: 220 }, emit: { x: 235, y: 220 }, result: { x: 440, y: 220 }, end: { x: 645, y: 220 } },
  }
}

/** 串行、并行与条件分支完整示例，供用户主动载入和复用。 */
export function allNodeFlow(): GraphDefinition {
  return structuredClone(allNodeExample) as GraphDefinition
}
