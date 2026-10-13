import type { FlowValidation, GraphDefinition, GraphIssue } from './types'

const base = '/api'

type ApiResponse<T> = { code: string; message: string; data: T | null }

function apiError(body: ApiResponse<unknown>, status: number): Error {
  const data = body.data
  const issues = data && typeof data === 'object' && 'issues' in data ? data.issues : data
  const issueText = Array.isArray(issues)
    ? (issues as GraphIssue[]).map((issue) => `${issue.code}: ${issue.message}`).join('; ')
    : ''
  return new Error(issueText || body.message || `HTTP ${status}`)
}

async function readJson<T>(response: Response): Promise<T> {
  const body = await response.json() as ApiResponse<T>
  if (!response.ok || body.code !== 'SUCCESS') throw apiError(body, response.status)
  return body.data as T
}

export async function loadDraft(sceneCode: string): Promise<{ graph: GraphDefinition; revision: number } | null> {
  const response = await fetch(`${base}/scenes/${encodeURIComponent(sceneCode)}/draft`)
  if (response.status === 404) return null
  return readJson(response)
}

export async function saveDraft(sceneCode: string, graph: GraphDefinition, expectedRevision: number) {
  return readJson<{ graph: GraphDefinition; revision: number }>(await fetch(
    `${base}/scenes/${encodeURIComponent(sceneCode)}/draft?expectedRevision=${expectedRevision}`,
    { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(graph) },
  ))
}

export async function validateFlow(sceneCode: string, graph: GraphDefinition): Promise<FlowValidation> {
  const response = await fetch(`${base}/scenes/${encodeURIComponent(sceneCode)}/validate`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(graph),
  })
  if (response.status === 422) {
    const body = await response.json() as ApiResponse<FlowValidation>
    if (body.code === 'GRAPH_INVALID' && body.data && body.data.valid === false
      && Array.isArray(body.data.issues)) return body.data
    throw apiError(body, response.status)
  }
  return readJson(response)
}

export async function publishFlow(sceneCode: string, expectedRevision: number) {
  return readJson<{ id: number; versionNumber: number; chainId: string }>(await fetch(
    `${base}/scenes/${encodeURIComponent(sceneCode)}/publish`,
    { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ expectedRevision }) },
  ))
}

export async function evaluateFlow(sceneCode: string, input: Record<string, unknown>) {
  return readJson<{ status: string; versionId: number; nodeResults: Record<string, unknown> }>(await fetch(
    `${base}/decisions/evaluate`,
    { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sceneCode, input }) },
  ))
}
