import { readApi } from './http'

export type DecisionNodeTrace = {
  requestId: string; sceneCode: string; flowVersion: number; nodeId: string
  startedAt: string; endedAt: string; status: string; errorCode: string | null
}
export type DecisionExecutionResult = {
  executionId: string | null; requestId: string; sceneCode: string; versionId: number
  status: string; errorCode: string | null
  businessResult: { conclusion: string; reasonCode: string; reason: string
    details: Record<string, unknown>; suggestedAction?: { code: string; parameters: Record<string, unknown> } | null } | null
  nodeResults: Record<string, unknown>; trace: DecisionNodeTrace[]
}
export type DecisionRecord = {
  executionId: string; requestId: string; sceneCode: string; versionId: number
  status: string; conclusion: string | null; errorCode: string | null; createdAt: string
  input?: Record<string, unknown> | null; result?: DecisionExecutionResult | null
  inputJson?: string | null; resultJson?: string | null
}
export type DecisionRecordPage = { records: DecisionRecord[]; total: number; page: number; pageSize: number }

/** 通用决策执行记录的分页与固定快照查询。 */
export async function listDecisionRecords(filters: { sceneCode?: string; requestId?: string; status?: string;
  page: number; pageSize: number }): Promise<DecisionRecordPage> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) if (value !== undefined && value !== '') {
    query.set(key, String(value))
  }
  return readApi<DecisionRecordPage>(await fetch(`/api/decision-records?${query}`))
}

export async function getDecisionRecord(executionId: string): Promise<DecisionRecord> {
  return readApi<DecisionRecord>(await fetch(`/api/decision-records/${encodeURIComponent(executionId)}`))
}
