import type { PublishedFlow } from '../services/flow'

export type InputSource =
  | { source: 'REQUEST_INPUT'; key: string }
  | { source: 'NODE_RESULT'; nodeId: string; path: string }

export type SubflowConfig = {
  flowCode: string
  versionId: number
  inputMappings: Record<string, InputSource>
}

/** 将编辑框内容转为后端图声明使用的唯一字段名。 */
export function parseRequiredInputs(text: string): string[] {
  const fields = text.split(/[,\n]/).map((field) => field.trim()).filter(Boolean)
  if (fields.some((field) => !/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(field))) {
    throw new Error('输入字段名须以字母开头，只包含字母、数字和下划线，最长 64 位')
  }
  return [...new Set(fields)]
}

/** 校验试运行输入与结果扩展字段必须是 JSON 对象。 */
export function parseJsonObject(text: string): Record<string, unknown> {
  let value: unknown
  try { value = JSON.parse(text) } catch { throw new Error('请输入有效的 JSON 对象') }
  if (value === null || Array.isArray(value) || typeof value !== 'object') {
    throw new Error('请输入 JSON 对象')
  }
  return value as Record<string, unknown>
}

export function readSubflowConfig(value: Record<string, unknown>): SubflowConfig {
  const mappings = value.inputMappings
  const inputMappings: Record<string, InputSource> = {}
  if (mappings && typeof mappings === 'object' && !Array.isArray(mappings)) {
    for (const [field, raw] of Object.entries(mappings)) {
      if (!raw || typeof raw !== 'object') continue
      const source = raw as Record<string, unknown>
      if (source.source === 'REQUEST_INPUT' && typeof source.key === 'string') {
        inputMappings[field] = { source: 'REQUEST_INPUT', key: source.key }
      } else if (source.source === 'NODE_RESULT'
        && typeof source.nodeId === 'string' && typeof source.path === 'string') {
        inputMappings[field] = { source: 'NODE_RESULT', nodeId: source.nodeId, path: source.path }
      }
    }
  }
  return {
    flowCode: typeof value.flowCode === 'string' ? value.flowCode : '',
    versionId: typeof value.versionId === 'number' ? value.versionId : 0,
    inputMappings,
  }
}

/** 仅在用户显式选择版本时更新固定 ID，并保留仍适用的输入映射。 */
export function selectPublishedVersion(current: SubflowConfig, published: PublishedFlow): SubflowConfig {
  const inputMappings: Record<string, InputSource> = {}
  for (const field of published.requiredInputs) {
    if (current.flowCode === published.flowCode && current.inputMappings[field]) {
      inputMappings[field] = current.inputMappings[field]
    }
  }
  return { flowCode: published.flowCode, versionId: published.id, inputMappings }
}

export function isVersionOutdated(config: SubflowConfig, published: PublishedFlow): boolean {
  return config.flowCode === published.flowCode && config.versionId > 0 && config.versionId !== published.id
}
