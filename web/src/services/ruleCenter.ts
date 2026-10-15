import { readApi } from './http'

export type RuleValueType = 'STRING' | 'BOOLEAN' | 'DECIMAL'
export type RuleCondition = { input: string; operator: 'EQ' | 'NE' | 'GT' | 'GTE' | 'LT' | 'LTE'; expected: unknown }
export type RuleOutcome = { resultCode: string; reasonCode: string; reason: string }
export type RuleDefinition = {
  inputs: { name: string; type: RuleValueType }[]
  resultCodes: string[]
  rows: { id: string; priority: number; conditions: RuleCondition[]; result: RuleOutcome }[]
  defaultResult: RuleOutcome
}
export type RuleSet = { code: string; name: string; draftRevision: number
  definition: RuleDefinition; currentVersionId: number | null }
export type RuleSetSummary = Pick<RuleSet, 'code' | 'name' | 'draftRevision' | 'currentVersionId'>
export type RuleSetVersion = { id: number; code: string; versionNumber: number; definition: RuleDefinition }
export type RuleSetPage = { records: RuleSetSummary[]; total: number; page: number; pageSize: number }
export type RuleEvaluation = RuleOutcome & { matchedRuleId: string | null; ruleSetCode: string; ruleVersionId: number }

const jsonHeaders = { 'Content-Type': 'application/json' }
const path = (code: string) => `/api/rule-sets/${encodeURIComponent(code)}`

/** 按后台分页契约查询规则集。 */
export async function listRuleSets(keyword = '', page = 1, pageSize = 10): Promise<RuleSetPage> {
  const query = new URLSearchParams({ keyword, page: String(page), pageSize: String(pageSize) })
  return readApi<RuleSetPage>(await fetch(`/api/rule-sets?${query}`))
}

/** 读取规则集当前草稿。 */
export async function getRuleSet(code: string): Promise<RuleSet> {
  return readApi<RuleSet>(await fetch(path(code)))
}

/** 创建规则集及首份草稿。 */
export async function createRuleSet(code: string, name: string, definition: RuleDefinition): Promise<RuleSet> {
  return readApi<RuleSet>(await fetch('/api/rule-sets', {
    method: 'POST', headers: jsonHeaders, body: JSON.stringify({ code, name, definition }),
  }))
}

/** 使用草稿修订号保护更新。 */
export async function updateRuleDraft(code: string, expectedRevision: number,
  definition: RuleDefinition): Promise<RuleSet> {
  return readApi<RuleSet>(await fetch(`${path(code)}/draft`, {
    method: 'PUT', headers: jsonHeaders, body: JSON.stringify({ expectedRevision, definition }),
  }))
}

/** 用当前草稿和模拟输入试算。 */
export async function testRuleSet(code: string, input: Record<string, unknown>): Promise<RuleEvaluation> {
  return readApi<RuleEvaluation>(await fetch(`${path(code)}/test`, {
    method: 'POST', headers: jsonHeaders, body: JSON.stringify({ input }),
  }))
}

/** 用指定不可变版本试算，以复现历史流程使用的规则结论。 */
export async function testRuleSetVersion(code: string, id: number,
  input: Record<string, unknown>): Promise<RuleEvaluation> {
  return readApi<RuleEvaluation>(await fetch(`${path(code)}/versions/${id}/test`, {
    method: 'POST', headers: jsonHeaders, body: JSON.stringify({ input }),
  }))
}

/** 将已保存的草稿修订号发布为不可变版本。 */
export async function publishRuleSet(code: string, expectedRevision: number): Promise<RuleSetVersion> {
  return readApi<RuleSetVersion>(await fetch(`${path(code)}/publish`, {
    method: 'POST', headers: jsonHeaders, body: JSON.stringify({ expectedRevision }),
  }))
}

/** 按固定版本 ID 查询已发布规则定义。 */
export async function getRuleSetVersion(id: number): Promise<RuleSetVersion> {
  return readApi<RuleSetVersion>(await fetch(`/api/rule-sets/versions/${id}`))
}
