import { readApi } from './http'

/** 变量中心输出给决策流程的强类型单值契约。 */
export type VariableValueType = 'STRING' | 'BOOLEAN' | 'DECIMAL'
export type VariableSourceType = 'REQUEST_INPUT' | 'FLOW_CONTEXT' | 'HTTP_GET' | 'DEMO_MARKETING'
export type VariableCategoryCode = 'USER' | 'TRANSACTION' | 'CAMPAIGN' | 'BUDGET' | 'RISK' | 'OTHER'
export type VariableCategory = { code: VariableCategoryCode; name: string }
export type VariableVersion = {
  id: number; variableCode: string; variableName: string; versionNumber: number
  categoryCode: VariableCategoryCode
  valueType: VariableValueType; sourceType: VariableSourceType; url: string | null; timeoutMs: number
  parameterNames: string[]; dependencyVersionIds: Record<string, number>; resultPath: string | null
}
export type VariablePage = { records: VariableVersion[]; total: number; page: number; pageSize: number }
export type VariableInput = Omit<VariableVersion, 'id' | 'versionNumber' | 'categoryCode'>
export type CreateVariableInput = VariableInput & { categoryCode?: VariableCategoryCode }
export type VariableFilters = { categoryCode?: VariableCategoryCode; sourceType?: VariableSourceType;
  valueType?: VariableValueType }
export type VariableTestResult = { variableCode: string; versionId: number; valueType: VariableValueType; value: string | boolean | number }

export async function listVariables(keyword = '', page = 1, pageSize = 10,
  filters: VariableFilters = {}): Promise<VariablePage> {
  const query = new URLSearchParams({ keyword, page: String(page), pageSize: String(pageSize) })
  if (filters.categoryCode) query.set('categoryCode', filters.categoryCode)
  if (filters.sourceType) query.set('sourceType', filters.sourceType)
  if (filters.valueType) query.set('valueType', filters.valueType)
  return readApi<VariablePage>(await fetch(`/api/variables?${query}`))
}

export async function listVariableCategories(): Promise<VariableCategory[]> {
  return readApi<VariableCategory[]>(await fetch('/api/variables/categories'))
}

export async function getVariable(code: string): Promise<VariableVersion> {
  return readApi<VariableVersion>(await fetch(`/api/variables/${encodeURIComponent(code)}`))
}

export async function getVariableVersion(id: number): Promise<VariableVersion> {
  return readApi<VariableVersion>(await fetch(`/api/variables/versions/${id}`))
}

export async function createVariable(input: CreateVariableInput): Promise<VariableVersion> {
  return readApi<VariableVersion>(await fetch('/api/variables', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
  }))
}

export async function updateVariableCategory(code: string,
  categoryCode: VariableCategoryCode): Promise<VariableVersion> {
  return readApi<VariableVersion>(await fetch(`/api/variables/${encodeURIComponent(code)}/category`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ categoryCode }),
  }))
}

export async function updateVariable(code: string, expectedVersion: number,
  input: VariableInput): Promise<VariableVersion> {
  return readApi<VariableVersion>(await fetch(`/api/variables/${encodeURIComponent(code)}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...input, expectedVersion }),
  }))
}

export async function testVariable(code: string, input: Record<string, unknown>): Promise<VariableTestResult> {
  return readApi<VariableTestResult>(await fetch(`/api/variables/${encodeURIComponent(code)}/test`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ input }),
  }))
}
