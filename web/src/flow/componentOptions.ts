import type { NodeKind } from './types'
import type { ComponentDefinition } from '../services/componentCatalog'

/** 组件节点与条件节点分别使用对应类型的 LiteFlow 组件。 */
export function componentOptionsFor(kind: NodeKind, catalog?: ComponentDefinition[]): string[] {
  if (catalog) {
    if (kind === 'COMPONENT') return catalog.filter((item) => !item.condition).map((item) => item.key)
    if (kind === 'CONDITION') return catalog.filter((item) => item.condition).map((item) => item.key)
    return []
  }
  switch (kind) {
    case 'COMPONENT':
      return ['emit-value', 'get-variable', 'execute-rule-set']
    case 'CONDITION':
      return ['is-positive', 'equals-value', 'compare-value', 'rule-result-equals']
    default:
      return []
  }
}
