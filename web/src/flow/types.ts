export type NodeKind = 'START' | 'END' | 'COMPONENT' | 'PARALLEL_SPLIT' |
  'PARALLEL_JOIN' | 'CONDITION' | 'CONDITION_JOIN' | 'RESULT' | 'SUBFLOW'

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

export type ContextBinding = {
  variableCode: string
  variableVersionId: number
  nodeId: string
  resultPath: string
}

export type GraphDefinition = {
  schemaVersion: 1
  requiredInputs?: string[]
  contextBindings?: ContextBinding[]
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
