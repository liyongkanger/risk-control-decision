import { readApi } from './http'

/** 变量中心输出给决策流程的强类型单值契约。 */
export type VariableValueType = 'STRING' | 'BOOLEAN' | 'DECIMAL'
export type VariableVersion = {
  id: number; variableCode: string; variableName: string; versionNumber: number
  valueType: VariableValueType; sourceType: 'HTTP_GET'; url: string; timeoutMs: number
  parameterNames: string[]; resultPath: string
}
export type VariablePage = { records: VariableVersion[]; total: number; page: number; pageSize: number }
export type VariableInput = Omit<VariableVersion, 'id' | 'versionNumber'>
export type VariableTestResult = { variableCode: string; versionId: number; valueType: VariableValueType; value: string | boolean | number }

export async function listVariables(keyword = '', page = 1, pageSize = 10): Promise<VariablePage> {
  const query = new URLSearchParams({ keyword, page: String(page), pageSize: String(pageSize) })
  return readApi<VariablePage>(await fetch(`/api/variables?${query}`))
}

export async function getVariable(code: string): Promise<VariableVersion> {
  return readApi<VariableVersion>(await fetch(`/api/variables/${encodeURIComponent(code)}`))
}

export async function getVariableVersion(id: number): Promise<VariableVersion> {
  return readApi<VariableVersion>(await fetch(`/api/variables/versions/${id}`))
}

export async function createVariable(input: VariableInput): Promise<VariableVersion> {
  return readApi<VariableVersion>(await fetch('/api/variables', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
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
