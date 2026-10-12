import type { FlowValidation, GraphDefinition, GraphIssue } from './types'

const base = '/api'

async function readJson<T>(response: Response): Promise<T> {
  const body = await response.json()
  if (!response.ok) {
    const issueText = Array.isArray(body.issues)
      ? (body.issues as GraphIssue[]).map((issue) => `${issue.code}: ${issue.message}`).join('; ')
      : ''
    throw new Error(issueText || body.message || `HTTP ${response.status}`)
  }
  return body as T
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
  if (response.status === 422) return response.json() as Promise<FlowValidation>
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
