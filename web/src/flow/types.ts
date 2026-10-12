export type NodeKind = 'START' | 'END' | 'COMPONENT' | 'PARALLEL_SPLIT' |
  'PARALLEL_JOIN' | 'CONDITION' | 'CONDITION_JOIN'

export type FlowNode = {
  id: string
  kind: NodeKind
  componentKey: string | null
  failurePolicy: 'FAIL_FAST' | null
  config: Record<string, unknown>
}

export type FlowEdge = {
  id: string
  source: string
  target: string
  branch: 'true' | 'false' | null
}

export type GraphDefinition = {
  schemaVersion: 1
  nodes: FlowNode[]
  edges: FlowEdge[]
  layout: Record<string, { x: number; y: number }>
}

export type GraphIssue = {
  code: string
  nodeId: string | null
  edgeId: string | null
  message: string
}

export type FlowValidation = {
  valid: boolean
  issues: GraphIssue[]
  compiledEl: string | null
}
