import { readApi } from './http'

/** 后台独立流程资产，与业务场景分开管理。 */
export type FlowAsset = {
  id: number
  flowCode: string
  flowName: string
  flowType: 'DECISION'
  publishedVersionId: number | null
  publishedVersionNumber: number | null
  updatetime: string
}

/** 独立流程资产的分页结果。 */
export type FlowPage = { records: FlowAsset[]; total: number; page: number; pageSize: number }

/** 子流程当前发布版本和已固化的必需输入。 */
export type PublishedFlow = { id: number; flowCode: string; versionNumber: number; requiredInputs: string[] }

/** 创建流程资产的可编辑字段。 */
export type CreateFlow = { flowCode: string; flowName: string }

export async function listFlows(keyword = '', page = 1, pageSize = 20): Promise<FlowPage> {
  const query = new URLSearchParams({ keyword, page: String(page), pageSize: String(pageSize) })
  return readApi<FlowPage>(await fetch(`/api/flows?${query}`))
}

export async function getFlow(flowCode: string): Promise<FlowAsset> {
  return readApi<FlowAsset>(await fetch(`/api/flows/${encodeURIComponent(flowCode)}`))
}

export async function getPublishedFlow(flowCode: string): Promise<PublishedFlow> {
  return readApi<PublishedFlow>(await fetch(`/api/flows/${encodeURIComponent(flowCode)}/published`))
}

export async function createFlow(request: CreateFlow): Promise<FlowAsset> {
  return readApi<FlowAsset>(await fetch('/api/flows', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request),
  }))
}
