import type { Graph } from '@antv/x6'
import type { ContextBinding, FlowEdge, FlowNode, GraphDefinition, NodeKind } from './types'

export function toGraphDefinition(graph: Pick<Graph, 'getNodes' | 'getEdges'>, requiredInputs?: string[],
                                  contextBindings?: ContextBinding[]): GraphDefinition {
  const layout: GraphDefinition['layout'] = {}
  const nodes: FlowNode[] = graph.getNodes().map((node) => {
    const data = (node.getData() ?? {}) as Partial<FlowNode>
    layout[node.id] = node.getPosition()
    return {
      id: node.id,
      kind: (data.kind ?? 'COMPONENT') as NodeKind,
      componentKey: data.componentKey ?? null,
      failurePolicy: data.failurePolicy ?? null,
      config: data.config ?? {},
    }
  })
  const edges: FlowEdge[] = graph.getEdges().map((edge) => {
    const data = (edge.getData() ?? {}) as Partial<FlowEdge>
    return {
      id: edge.id,
      source: edge.getSourceCellId() ?? '',
      target: edge.getTargetCellId() ?? '',
      branch: data.branch ?? null,
    }
  })
  return { schemaVersion: 1, ...(requiredInputs ? { requiredInputs } : {}),
    ...(contextBindings ? { contextBindings } : {}), nodes, edges, layout }
}
