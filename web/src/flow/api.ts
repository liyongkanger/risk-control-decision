import type { FlowValidation, GraphDefinition } from './types'
import { readApi, type ApiResponse } from '../services/http'

export type BusinessResult = {
  conclusion: string
  reasonCode: string
  reason: string
  details: Record<string, unknown>
  suggestedAction: { code: string; parameters: Record<string, unknown> } | null
}

export type DecisionRunResult = {
  requestId: string
  sceneCode: string
  versionId: number
  status: 'SUCCEEDED' | 'FAILED'
  businessResult: BusinessResult | null
  nodeResults: Record<string, unknown>
  trace: Array<{ nodeId: string; status: string; errorCode: string | null }>
  errorCode: string | null
}

const base = '/api'

/** 场景入口或独立流程资产对应的编辑目标。 */
export type FlowTarget = { kind: 'scene' | 'flow'; code: string }

function targetPath(target: FlowTarget): string {
  return `${base}/${target.kind === 'scene' ? 'scenes' : 'flows'}/${encodeURIComponent(target.code)}`
}

export async function loadTargetDraft(target: FlowTarget): Promise<{ graph: GraphDefinition; revision: number } | null> {
  const response = await fetch(`${targetPath(target)}/draft`)
  if (response.status === 404) return null
  return readApi(response)
}

export async function loadDraft(sceneCode: string) {
  return loadTargetDraft({ kind: 'scene', code: sceneCode })
}

export async function saveTargetDraft(target: FlowTarget, graph: GraphDefinition, expectedRevision: number) {
  return readApi<{ graph: GraphDefinition; revision: number }>(await fetch(
    `${targetPath(target)}/draft?expectedRevision=${expectedRevision}`,
    { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(graph) },
  ))
}

export async function saveDraft(sceneCode: string, graph: GraphDefinition, expectedRevision: number) {
  return saveTargetDraft({ kind: 'scene', code: sceneCode }, graph, expectedRevision)
}

export async function validateTargetFlow(target: FlowTarget, graph: GraphDefinition): Promise<FlowValidation> {
  const response = await fetch(`${targetPath(target)}/validate`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(graph),
  })
  if (response.status === 422) {
    const body = await response.json() as ApiResponse<FlowValidation>
    if (body.code === 'GRAPH_INVALID' && body.data && body.data.valid === false
      && Array.isArray(body.data.issues)) return body.data
    throw new Error(body.message || '流程校验请求无效')
  }
  return readApi(response)
}

export async function validateFlow(sceneCode: string, graph: GraphDefinition): Promise<FlowValidation> {
  return validateTargetFlow({ kind: 'scene', code: sceneCode }, graph)
}

export async function publishTargetFlow(target: FlowTarget, expectedRevision: number) {
  return readApi<{ id: number; versionNumber: number; chainId: string }>(await fetch(
    `${targetPath(target)}/publish`,
    { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ expectedRevision }) },
  ))
}

export async function publishFlow(sceneCode: string, expectedRevision: number) {
  return publishTargetFlow({ kind: 'scene', code: sceneCode }, expectedRevision)
}

export async function evaluateFlow(sceneCode: string, input: Record<string, unknown>) {
  return readApi<DecisionRunResult>(await fetch(
    `${base}/decisions/evaluate`,
    { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sceneCode, input }) },
  ))
}
