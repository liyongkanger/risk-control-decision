import { readApi } from './http'

/** 后端组件目录中的字段契约。 */
export type ComponentConfigField = { name: string; label: string; type: string; required: boolean }
export type ComponentDefinition = {
  key: string; displayName: string; description: string; outputType: 'ANY' | 'BOOLEAN' | 'VARIABLE_VALUE' | 'RULE_RESULT'
  category: 'DATA' | 'CONDITION' | 'RULE'
  condition: boolean
  configFields: ComponentConfigField[]; defaultConfig: Record<string, unknown>
}
export type ComponentCatalogPage = {
  records: ComponentDefinition[]; total: number; page: number; pageSize: number
}

export async function listComponentDefinitions(): Promise<ComponentDefinition[]> {
  return readApi<ComponentDefinition[]>(await fetch('/api/components'))
}

/** 后台目录页由后端筛选和分页，编辑器仍使用完整目录。 */
export async function searchComponentDefinitions(keyword = '', nodeKind = '', page = 1,
  pageSize = 10, category = ''): Promise<ComponentCatalogPage> {
  const query = new URLSearchParams({ keyword, nodeKind, page: String(page), pageSize: String(pageSize) })
  if (category) query.set('category', category)
  return readApi<ComponentCatalogPage>(await fetch(`/api/components/page?${query}`))
}
